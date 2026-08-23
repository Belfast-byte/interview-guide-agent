package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankSearchTool;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 将模型原生响应转换为经过来源校验的 Agent 动作。 */
@Slf4j
@Component
public class AdaptiveAgentResponseMapper {

  private static final int SINGLE_TOOL_CALL_COUNT = 1;
  private static final int MAX_CONTENT_LENGTH = 2_000;
  private static final int MAX_REASON_LENGTH = 500;
  private static final TypeReference<Map<String, Object>> TOOL_ARGUMENTS_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<List<QuestionBankQuestion>> QUESTION_RESULTS_TYPE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final BeanOutputConverter<AgentStepOutput> outputConverter;

  public AdaptiveAgentResponseMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.outputConverter = new BeanOutputConverter<>(AgentStepOutput.class);
  }

  String format() {
    return outputConverter.getFormat();
  }

  AgentAction map(ChatResponse response, ReActModelContext context) {
    AssistantMessage message = response.getResult().getOutput();
    if (message.hasToolCalls()) {
      return mapToolCall(message, context);
    }
    return mapText(message.getText(), context);
  }

  /**
   * 流式路径的文本映射：流式调用不承载工具调用，直接按完整文本解析。
   */
  AgentAction mapText(String rawText, ReActModelContext context) {
    AgentStepOutput output = normalize(outputConverter.convert(rawText));
    validateRequiredFields(output);
    if (output.type() == AgentResponseType.FINISH) {
      return mapFinish(output, context);
    }
    return mapAsk(output, context);
  }

  private ToolCallAction mapToolCall(
      AssistantMessage message,
      ReActModelContext context
  ) {
    List<AssistantMessage.ToolCall> toolCalls = message.getToolCalls();
    AssistantMessage.ToolCall toolCall = toolCalls.getFirst();
    if (toolCalls.size() > SINGLE_TOOL_CALL_COUNT) {
      log.warn(
          "adaptive_agent_parallel_tools_truncated sessionId={} inputTurn={} "
              + "selectedTool={} discardedTools={}",
          context.request().sessionId(),
          context.request().inputTurnIndex(),
          toolCall.name(),
          toolCalls.subList(SINGLE_TOOL_CALL_COUNT, toolCalls.size()).stream()
              .map(AssistantMessage.ToolCall::name)
              .toList()
      );
    }
    return new ToolCallAction(
        toolCall.name(),
        readArguments(toolCall.arguments()),
        "Call " + toolCall.name() + " for objective interview context"
    );
  }

  private AgentStepOutput normalize(AgentStepOutput output) {
    if (output == null) {
      throw new ModelOutputRejectionException("Agent response is incomplete");
    }
    return new AgentStepOutput(
        output.type(),
        output.content(),
        output.reason(),
        normalizeNullable(output.sourceQuestionId()),
        normalizeNullable(output.sourceDifficulty()),
        normalizeNullable(output.codeSourceId()),
        normalizeNullable(output.codeAnchor()),
        normalizeNullable(output.codeFactUsage())
    );
  }

  private String normalizeNullable(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void validateRequiredFields(AgentStepOutput output) {
    if (output.type() == null
        || output.content() == null
        || output.content().isBlank()
        || output.reason() == null
        || output.reason().isBlank()) {
      throw new ModelOutputRejectionException("Agent response is incomplete");
    }
    if (output.content().length() > MAX_CONTENT_LENGTH
        || output.reason().length() > MAX_REASON_LENGTH) {
      throw new ModelOutputRejectionException("Agent response is too long");
    }
  }

  private RespondAction mapFinish(AgentStepOutput output, ReActModelContext context) {
    if (!AdaptiveInterviewSession.canFinishEarly(
        context.request().interviewerContext().maxTurns(),
        context.request().inputTurnIndex()
    )) {
      throw new ModelOutputRejectionException(
          "Interview turns have not reached the early-finish threshold; keep asking"
      );
    }
    return RespondAction.finish(output.content(), output.reason());
  }

  private RespondAction mapAsk(AgentStepOutput output, ReActModelContext context) {
    if (output.type() != AgentResponseType.ASK) {
      throw new ModelOutputRejectionException("Unsupported agent response type");
    }
    CodeQuestionProvenance codeProvenance = codeProvenance(output, context);
    if (output.sourceQuestionId() == null && output.sourceDifficulty() == null) {
      return codeProvenance == null
          ? RespondAction.ask(output.content(), output.reason())
          : RespondAction.askFromCode(output.content(), output.reason(), codeProvenance);
    }
    return mapQuestionBankAsk(output, context, codeProvenance);
  }

  private RespondAction mapQuestionBankAsk(
      AgentStepOutput output,
      ReActModelContext context,
      CodeQuestionProvenance codeProvenance
  ) {
    if (output.sourceQuestionId() == null
        || output.sourceDifficulty() == null
        || codeProvenance != null) {
      throw new ModelOutputRejectionException("Question provenance is incomplete");
    }
    QuestionBankQuestion sourceQuestion = findSourceQuestion(output, context);
    return RespondAction.ask(
        output.content(),
        output.reason(),
        new QuestionProvenance(sourceQuestion.stableId(), sourceQuestion.difficulty())
    );
  }

  private QuestionBankQuestion findSourceQuestion(
      AgentStepOutput output,
      ReActModelContext context
  ) {
    return context.observations().stream()
        .filter(ToolObservation::accepted)
        .filter(observation -> QuestionBankSearchTool.NAME.equals(observation.toolName()))
        .flatMap(observation -> readQuestions(observation.output()).stream())
        .filter(question -> question.stableId().equals(output.sourceQuestionId()))
        .filter(question -> question.difficulty().equals(output.sourceDifficulty()))
        .findFirst()
        .orElseThrow(() -> new ModelOutputRejectionException(
            "Question provenance does not match an accepted tool result"
        ));
  }

  private CodeQuestionProvenance codeProvenance(
      AgentStepOutput output,
      ReActModelContext context
  ) {
    CodeFactUsage usage = parseCodeFactUsage(output.codeFactUsage());
    if (output.codeSourceId() == null && output.codeAnchor() == null && usage == null) {
      return null;
    }
    ProjectInterviewContext project = context.request().interviewerContext().project();
    if (output.codeSourceId() == null
        || output.codeAnchor() == null
        || usage == null
        || project == null) {
      throw new ModelOutputRejectionException("Code question provenance is incomplete");
    }
    if (!matchesCodeProvenance(output, usage, project)) {
      throw new ModelOutputRejectionException(
          "Code question provenance does not match an accepted analysis artifact"
      );
    }
    return new CodeQuestionProvenance(output.codeSourceId(), output.codeAnchor(), usage);
  }

  private CodeFactUsage parseCodeFactUsage(String value) {
    if (value == null) {
      return null;
    }
    try {
      return CodeFactUsage.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ModelOutputRejectionException("Code fact usage is invalid");
    }
  }

  private boolean matchesCodeProvenance(
      AgentStepOutput output,
      CodeFactUsage usage,
      ProjectInterviewContext project
  ) {
    return switch (usage) {
      case QUESTION_SOURCE -> project.scenarios().stream()
          .anyMatch(scenario -> scenario.scenarioId().equals(output.codeSourceId())
              && scenario.anchor().equals(output.codeAnchor()));
      case CLAIM_VERIFICATION -> project.claims().stream()
          .filter(claim -> claim.claimId().equals(output.codeSourceId()))
          .flatMap(claim -> claim.codeFacts().stream())
          .anyMatch(fact -> output.codeAnchor().equals(fact.anchor()));
    };
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
      String codeFactUsage
  ) {}

  /** 模型输出校验失败时触发一次带拒绝原因的改写。 */
  static final class ModelOutputRejectionException extends BusinessException {

    private ModelOutputRejectionException(String message) {
      super(ErrorCode.AI_SERVICE_ERROR, message);
    }
  }
}
