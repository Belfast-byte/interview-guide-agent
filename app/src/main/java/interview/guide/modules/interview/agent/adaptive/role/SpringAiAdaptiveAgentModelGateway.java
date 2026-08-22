package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentModelGateway;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchTool;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的自适应 Agent 模型网关，按角色组装 Prompt 并调用 LLM。
 */
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
      PromptLoader promptLoader
  ) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.inputTokenBudget = inputTokenBudget;
    this.roleRegistry = roleRegistry;
    this.toolGateway = toolGateway;
    this.systemPromptTemplate = promptLoader.loadTemplate(properties.getSystemPromptPath());
    this.userPromptTemplate = promptLoader.loadTemplate(properties.getUserPromptPath());
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
      } catch (ModelOutputRejectionException e) {
        ReActModelContext retryContext = withOutputRejection(context, e.getMessage());
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

  private ReActModelContext withOutputRejection(
      ReActModelContext context,
      String reason
  ) {
    ToolObservation rejection = new ToolObservation(
        "model_output_validation",
        Map.of(),
        false,
        null,
        reason + "; fix the output and respond again"
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
        // 关闭 Spring AI 2.0 自动注册的 ToolCallingAdvisor：
        // 工具必须由 BoundedReActRuntime 经 ToolGateway 手动执行，
        // 不能让 ChatClient 在内部消化 tool call（占位 callback 会抛异常）。
        .advisors(advisor -> advisor.param(
            ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
            false
        ))
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
      throw new ModelOutputRejectionException("Agent response is incomplete");
    }
    if (output.content().length() > MAX_RESPONSE_LENGTH
        || output.reason().length() > MAX_RESPONSE_LENGTH) {
      throw new ModelOutputRejectionException("Agent response is too long");
    }
    if (output.type() != AgentResponseType.ASK) {
      throw new ModelOutputRejectionException(
          "Agent must ask until the planned turns are complete"
      );
    }
    long questionMarks = output.content().chars()
        .filter(character -> character == '?' || character == '？')
        .count();
    if (questionMarks != 1
        || output.content().contains("\n")
        || output.content().contains("\r")) {
      throw new ModelOutputRejectionException(
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
      throw new ModelOutputRejectionException("Question provenance is incomplete");
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
        .orElseThrow(() -> new ModelOutputRejectionException(
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
      throw new ModelOutputRejectionException("Code question provenance is incomplete");
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
      throw new ModelOutputRejectionException(
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

  /**
   * 模型输出校验失败（格式/来源不匹配等）时抛出，触发一次带拒绝原因的改写重试；
   * 重写后仍失败才作为普通业务异常向上抛。
   */
  private static final class ModelOutputRejectionException extends BusinessException {

    private ModelOutputRejectionException(String message) {
      super(ErrorCode.AI_SERVICE_ERROR, message);
    }
  }
}
