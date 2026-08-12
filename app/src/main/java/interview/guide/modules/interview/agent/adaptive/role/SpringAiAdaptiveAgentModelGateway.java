package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentModelGateway;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.helpers.NOPLogger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpringAiAdaptiveAgentModelGateway implements AgentModelGateway {

  private static final int MAX_RESPONSE_LENGTH = 500;

  private final LlmProviderRegistry llmProviderRegistry;
  private final StructuredOutputInvoker structuredOutputInvoker;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<AgentStepOutput> outputConverter;

  public SpringAiAdaptiveAgentModelGateway(
      LlmProviderRegistry llmProviderRegistry,
      StructuredOutputInvoker structuredOutputInvoker,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.structuredOutputInvoker = structuredOutputInvoker;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
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
    AgentStepOutput output;
    try {
      String contextJson = serializeContext(context);
      String systemPrompt = systemPromptTemplate.render()
          + "\n\n"
          + outputConverter.getFormat();
      String userPrompt = userPromptTemplate.render(Map.of("contextJson", contextJson));
      ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(
          context.request().llmProvider()
      );
      output = structuredOutputInvoker.invoke(
          chatClient,
          systemPrompt,
          userPrompt,
          outputConverter,
          ErrorCode.AI_SERVICE_ERROR,
          "Agent 面试决策失败：",
          "adaptive_agent_interview_decision",
          NOPLogger.NOP_LOGGER
      );
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "interviewer",
          context.request().sessionId(),
          inputTurn(context),
          e.getCode(),
          startedNanos
      );
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 面试决策失败", e);
    }

    try {
      RespondAction action = validateAndMap(output, context);
      telemetry.modelCallSucceeded("interviewer", action.type().name(), startedNanos);
      return action;
    } catch (BusinessException e) {
      telemetry.modelCallFailed(
          "interviewer",
          context.request().sessionId(),
          inputTurn(context),
          e.getCode(),
          startedNanos
      );
      throw e;
    }
  }

  private int inputTurn(ReActModelContext context) {
    return context.request().candidateAnswer() == null
        ? 0
        : context.request().candidateAnswer().turnIndex();
  }

  private String serializeContext(ReActModelContext context) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("jd", context.request().jd());
    values.put("resume", context.request().resume());
    values.put("currentTurn", context.request().turns().size());
    values.put("maxTurns", context.request().maxTurns());
    values.put("turns", context.request().turns());
    values.put("candidateAnswer", context.request().candidateAnswer());
    values.put("observations", context.observations());
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 上下文序列化失败", e);
    }
  }

  private RespondAction validateAndMap(AgentStepOutput output, ReActModelContext context) {
    if (output.type() == null
        || output.content() == null
        || output.content().isBlank()
        || output.reason() == null
        || output.reason().isBlank()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 返回了不完整的响应");
    }
    if (output.content().length() > MAX_RESPONSE_LENGTH
        || output.reason().length() > MAX_RESPONSE_LENGTH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 响应超过长度限制");
    }
    if (context.request().candidateAnswer() == null
        && output.type() != AgentResponseType.ASK) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 首次响应必须是面试问题");
    }
    if (output.type() == AgentResponseType.ASK) {
      long questionMarks = output.content().chars()
          .filter(character -> character == '?' || character == '？')
          .count();
      if (questionMarks != 1
          || output.content().contains("\n")
          || output.content().contains("\r")) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 每次只能返回一个单行问题");
      }
      return RespondAction.ask(output.content(), output.reason());
    }
    return RespondAction.finish(output.content(), output.reason());
  }

  record AgentStepOutput(
      AgentResponseType type,
      String content,
      String reason
  ) {}
}
