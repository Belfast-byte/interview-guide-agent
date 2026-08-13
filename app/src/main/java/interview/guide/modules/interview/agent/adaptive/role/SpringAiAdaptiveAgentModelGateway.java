package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentModelGateway;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpringAiAdaptiveAgentModelGateway implements AgentModelGateway {

  private static final int MAX_RESPONSE_LENGTH = 500;
  private static final TypeReference<Map<String, Object>> TOOL_ARGUMENTS_TYPE =
      new TypeReference<>() {};

  private final LlmProviderRegistry llmProviderRegistry;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AgentRoleRegistry roleRegistry;
  private final ToolGateway toolGateway;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<AgentStepOutput> outputConverter;

  public SpringAiAdaptiveAgentModelGateway(
      LlmProviderRegistry llmProviderRegistry,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AgentRoleRegistry roleRegistry,
      ToolGateway toolGateway,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.roleRegistry = roleRegistry;
    this.toolGateway = toolGateway;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(properties.getUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.outputConverter = new BeanOutputConverter<>(AgentStepOutput.class);
  }

  @Override
  public AgentAction nextAction(ReActModelContext context) {
    long startedNanos = System.nanoTime();
    ChatResponse response;
    try {
      response = callModel(context);
    } catch (Exception e) {
      recordFailure(context, startedNanos, ErrorCode.AI_SERVICE_ERROR.getCode());
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent interview model call failed",
          e
      );
    }

    try {
      AgentAction action = mapResponse(response, context);
      telemetry.modelCallSucceeded(
          "interviewer",
          action instanceof RespondAction respond ? respond.type().name() : "TOOL_CALL",
          startedNanos
      );
      return action;
    } catch (BusinessException e) {
      recordFailure(context, startedNanos, e.getCode());
      throw e;
    } catch (Exception e) {
      recordFailure(context, startedNanos, ErrorCode.AI_SERVICE_ERROR.getCode());
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent interview decision failed",
          e
      );
    }
  }

  private ChatResponse callModel(ReActModelContext context) {
    String systemPrompt = systemPromptTemplate.render()
        + "\n\n"
        + outputConverter.getFormat();
    String userPrompt = userPromptTemplate.render(Map.of(
        "contextJson",
        serializeContext(context)
    ));
    ChatClient chatClient = llmProviderRegistry.getPlainChatClient(
        context.request().llmProvider()
    );
    return chatClient.prompt()
        .system(systemPrompt)
        .user(userPrompt)
        .options(ToolCallingChatOptions.builder()
            .toolCallbacks(toolGateway.callbacksFor(
                roleRegistry.get(context.request().role())
            )))
        .call()
        .chatResponse();
  }

  private void recordFailure(
      ReActModelContext context,
      long startedNanos,
      int errorCode
  ) {
    telemetry.modelCallFailed(
        "interviewer",
        context.request().sessionId(),
        inputTurn(context),
        errorCode,
        startedNanos
    );
  }

  private AgentAction mapResponse(ChatResponse response, ReActModelContext context) {
    AssistantMessage message = response.getResult().getOutput();
    if (message.hasToolCalls()) {
      if (message.getToolCalls().size() != 1) {
        throw new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "Agent can call only one tool per step"
        );
      }
      AssistantMessage.ToolCall toolCall = message.getToolCalls().getFirst();
      return new ToolCallAction(
          toolCall.name(),
          readArguments(toolCall.arguments()),
          "Call " + toolCall.name() + " for objective interview context"
      );
    }
    AgentStepOutput output = outputConverter.convert(message.getText());
    return validateAndMap(output);
  }

  private Map<String, Object> readArguments(String arguments) {
    try {
      Map<String, Object> values = objectMapper.readValue(arguments, TOOL_ARGUMENTS_TYPE);
      if (values == null) {
        throw new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "Agent tool arguments must be a JSON object"
        );
      }
      return values;
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool arguments are invalid",
          e
      );
    }
  }

  private int inputTurn(ReActModelContext context) {
    return context.request().inputTurnIndex();
  }

  private String serializeContext(ReActModelContext context) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "interviewContext", context.request().interviewerContext(),
          "observations", context.observations()
      ));
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent context serialization failed",
          e
      );
    }
  }

  private RespondAction validateAndMap(AgentStepOutput output) {
    if (output.type() == null
        || output.content() == null
        || output.content().isBlank()
        || output.reason() == null
        || output.reason().isBlank()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent response is incomplete");
    }
    if (output.content().length() > MAX_RESPONSE_LENGTH
        || output.reason().length() > MAX_RESPONSE_LENGTH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent response is too long");
    }
    if (output.type() != AgentResponseType.ASK) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent must ask until the planned turns are complete"
      );
    }
    long questionMarks = output.content().chars()
        .filter(character -> character == '?' || character == '？')
        .count();
    if (questionMarks != 1
        || output.content().contains("\n")
        || output.content().contains("\r")) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent must return exactly one single-line question"
      );
    }
    return RespondAction.ask(output.content(), output.reason());
  }

  record AgentStepOutput(
      AgentResponseType type,
      String content,
      String reason
  ) {}
}
