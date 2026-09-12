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
    String submittedCode
) {

  /** 评估与历史召回共用原始问答上下文，不携带评分或候选人画像。 */
  public record AnswerContext(int turnIndex, String question, String answer, String submittedCode) {
    public AnswerContext(int turnIndex, String question, String answer) {
      this(turnIndex, question, answer, null);
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
        null, CodeRepairTask.QuestionType.TEXT, null, null, null);
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
