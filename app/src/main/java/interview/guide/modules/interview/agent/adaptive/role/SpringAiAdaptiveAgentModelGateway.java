package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.QuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentModelGateway;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
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
  private static final TypeReference<List<QuestionBankQuestion>>
      QUESTION_RESULTS_TYPE = new TypeReference<>() {};

  private final LlmProviderRegistry llmProviderRegistry;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final AdaptiveInputTokenBudget inputTokenBudget;
  private final AgentRoleRegistry roleRegistry;
  private final ToolGateway toolGateway;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final BeanOutputConverter<AgentStepOutput> outputConverter;

  public SpringAiAdaptiveAgentModelGateway(
      LlmProviderRegistry llmProviderRegistry,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      AdaptiveInputTokenBudget inputTokenBudget,
      AgentRoleRegistry roleRegistry,
      ToolGateway toolGateway,
      AdaptiveAgentProperties properties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
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
      AgentAction action;
      try {
        action = mapResponse(response, context);
      } catch (CodeQuestionProvenanceException e) {
        ReActModelContext retryContext = withCodeProvenanceRejection(context, e.getMessage());
        action = mapResponse(callModel(retryContext), retryContext);
      }
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

  private ReActModelContext withCodeProvenanceRejection(
      ReActModelContext context,
      String reason
  ) {
    ToolObservation rejection = new ToolObservation(
        "code_provenance_validation",
        Map.of(),
        false,
        null,
        reason + "; choose an exact sourceId and anchor from project context"
    );
    return new ReActModelContext(
        context.request(),
        Stream.concat(context.observations().stream(), Stream.of(rejection)).toList()
    );
  }

  private ChatResponse callModel(ReActModelContext context) {
    String systemPrompt = systemPromptTemplate.render()
        + "\n\n"
        + outputConverter.getFormat();
    String userPrompt = userPromptTemplate.render(Map.of(
        "contextJson",
        serializeContext(context)
    ));
    inputTokenBudget.verify("interviewer", systemPrompt, userPrompt);
    ChatClient chatClient = telemetry.observeTokenUsage(
        llmProviderRegistry.getPlainChatClient(context.request().llmProvider()),
        "interviewer",
        context.request().sessionId()
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
    return validateAndMap(output, context);
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

  private RespondAction validateAndMap(
      AgentStepOutput output,
      ReActModelContext context
  ) {
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
    CodeQuestionProvenance codeProvenance = codeProvenance(output, context);
    if (output.sourceQuestionId() == null
        && output.sourceDifficulty() == null) {
      return codeProvenance == null
          ? RespondAction.ask(output.content(), output.reason())
          : RespondAction.askFromCode(
              output.content(),
              output.reason(),
              codeProvenance
          );
    }
    if (output.sourceQuestionId() == null
        || output.sourceDifficulty() == null
        || codeProvenance != null) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Question provenance is incomplete"
      );
    }
    QuestionBankQuestion sourceQuestion = context.observations().stream()
        .filter(ToolObservation::accepted)
        .filter(observation -> QuestionBankSearchTool.NAME.equals(
            observation.toolName()
        ))
        .flatMap(observation -> readQuestions(observation.output()).stream())
        .filter(question -> question.stableId().equals(output.sourceQuestionId()))
        .filter(question -> question.difficulty().equals(output.sourceDifficulty()))
        .filter(question -> question.question().equals(output.content()))
        .findFirst()
        .orElseThrow(() -> new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "Question provenance does not match an accepted tool result"
        ));
    return RespondAction.ask(
        output.content(),
        output.reason(),
        new QuestionProvenance(
            sourceQuestion.stableId(),
            sourceQuestion.difficulty()
        )
    );
  }

  private CodeQuestionProvenance codeProvenance(
      AgentStepOutput output,
      ReActModelContext context
  ) {
    if (output.codeSourceId() == null
        && output.codeAnchor() == null
        && output.codeFactUsage() == null) {
      return null;
    }
    if (output.codeSourceId() == null
        || output.codeAnchor() == null
        || output.codeFactUsage() == null
        || context.request().interviewerContext().project() == null) {
      throw new CodeQuestionProvenanceException("Code question provenance is incomplete");
    }
    ProjectInterviewContext project = context.request().interviewerContext().project();
    boolean matched = switch (output.codeFactUsage()) {
      case QUESTION_SOURCE -> project.scenarios().stream()
          .anyMatch(scenario -> scenario.scenarioId().equals(output.codeSourceId())
              && scenario.anchor().equals(output.codeAnchor()));
      case CLAIM_VERIFICATION -> project.claims().stream()
          .filter(claim -> claim.claimId().equals(output.codeSourceId()))
          .flatMap(claim -> claim.codeFacts().stream())
          .anyMatch(fact -> output.codeAnchor().equals(fact.anchor()));
    };
    if (!matched) {
      throw new CodeQuestionProvenanceException(
          "Code question provenance does not match an accepted analysis artifact"
      );
    }
    return new CodeQuestionProvenance(
        output.codeSourceId(),
        output.codeAnchor(),
        output.codeFactUsage()
    );
  }

  private List<QuestionBankQuestion> readQuestions(String output) {
    try {
      return objectMapper.readValue(output, QUESTION_RESULTS_TYPE);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Question bank result is invalid",
          e
      );
    }
  }

  record AgentStepOutput(
      AgentResponseType type,
      String content,
      String reason,
      String sourceQuestionId,
      String sourceDifficulty,
      String codeSourceId,
      String codeAnchor,
      CodeFactUsage codeFactUsage
  ) {}

  private static final class CodeQuestionProvenanceException extends BusinessException {

    private CodeQuestionProvenanceException(String message) {
      super(ErrorCode.AI_SERVICE_ERROR, message);
    }
  }
}
