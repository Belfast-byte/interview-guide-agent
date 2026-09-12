package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import java.util.List;

/**
 * 自适应面试单轮领域对象，记录问题、回答、工具调用和评估摘要。
 */
public record AdaptiveInterviewTurn(
    int turnIndex,
    Integer dimensionOrder,
    String question,
    String questionReason,
    String answer,
    AgentResponseType responseType,
    String responseContent,
    String decisionReason,
    TurnProvenance provenance,
    List<AdoptedRubricSource> adoptedRubrics,
    AnswerProcessingStatus answerStatus,
    String answerError,
    CodeRepairTask.QuestionType questionType,
    CodeRepairTask codeTask,
    Integer codeTaskTurnIndex,
    String submittedCode,
    AssessmentFeedback assessmentFeedback
) {

  /** 由正式 Assessment 和 Evidence 读取投影，不在 Turn 中另存评级。 */
  public record AssessmentFeedback(
      interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel depthLevel,
      String rationale,
      interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview codeReview,
      List<interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote> evidenceQuotes
  ) {
    public AssessmentFeedback { evidenceQuotes = List.copyOf(evidenceQuotes); }
  }

  public AdaptiveInterviewTurn withAssessmentFeedback(AssessmentFeedback feedback) {
    return new AdaptiveInterviewTurn(turnIndex, dimensionOrder, question, questionReason, answer,
        responseType, responseContent, decisionReason, provenance, adoptedRubrics, answerStatus,
        answerError, questionType, codeTask, codeTaskTurnIndex, submittedCode, feedback);
  }

  /** 默认仅传原始问答；Episode 可显式补入作答前已公开的反馈，不携带画像。 */
  public record AnswerContext(int turnIndex, String question, String answer, String submittedCode,
      interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview codeReview,
      String feedbackRationale) {
    public AnswerContext(int turnIndex, String question, String answer, String submittedCode) {
      this(turnIndex, question, answer, submittedCode, null, null);
    }

    public AnswerContext(int turnIndex, String question, String answer) {
      this(turnIndex, question, answer, null, null, null);
    }
  }

  public AnswerContext answerContext() {
    return new AnswerContext(turnIndex, question, answer, submittedCode);
  }

  public AdaptiveInterviewTurn(int turnIndex, Integer dimensionOrder, String question,
      String questionReason, String answer, AgentResponseType responseType,
      String responseContent, String decisionReason, TurnProvenance provenance,
      List<AdoptedRubricSource> adoptedRubrics) {
    this(turnIndex, dimensionOrder, question, questionReason, answer, responseType,
        responseContent, decisionReason, provenance, adoptedRubrics,
        responseType != null ? AnswerProcessingStatus.COMPLETED
            : answer == null ? AnswerProcessingStatus.WAITING : AnswerProcessingStatus.RETRYABLE,
        null, CodeRepairTask.QuestionType.TEXT, null, null, null, null);
  }

  public AdaptiveInterviewTurn {
    provenance.validateForTurn(turnIndex);
    adoptedRubrics = List.copyOf(adoptedRubrics);
  }

  public AdaptiveInterviewTurn(
      int turnIndex,
      Integer dimensionOrder,
      String question,
      String questionReason,
      String answer,
      AgentResponseType responseType,
      String responseContent,
      String decisionReason,
      TurnProvenance provenance
  ) {
    this(
        turnIndex, dimensionOrder, question, questionReason, answer,
        responseType, responseContent, decisionReason, provenance, List.of());
  }

  public AdaptiveInterviewTurn(
      int turnIndex,
      Integer dimensionOrder,
      String question,
      String questionReason,
      String answer,
      AgentResponseType responseType,
      String responseContent,
      String decisionReason
  ) {
    this(
        turnIndex,
        dimensionOrder,
        question,
        questionReason,
        answer,
        responseType,
        responseContent,
        decisionReason,
        defaultProvenance(turnIndex),
        List.of()
    );
  }

  private static TurnProvenance defaultProvenance(int turnIndex) {
    return TurnProvenance.initial();
  }
}
