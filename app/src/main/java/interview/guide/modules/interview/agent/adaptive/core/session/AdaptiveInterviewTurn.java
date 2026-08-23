package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;

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
    TurnProvenance provenance
) {

  public AdaptiveInterviewTurn {
    provenance.validateForTurn(turnIndex);
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
        defaultProvenance(turnIndex)
    );
  }

  private static TurnProvenance defaultProvenance(int turnIndex) {
    return TurnProvenance.initial();
  }
}
