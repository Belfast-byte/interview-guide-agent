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
    List<AdoptedRubricSource> adoptedRubrics
) {

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
