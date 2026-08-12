package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;

public record AdaptiveInterviewTurnResponse(
    int turnIndex,
    Integer dimensionOrder,
    String question,
    String answer
) {

  static AdaptiveInterviewTurnResponse from(AdaptiveInterviewTurn turn) {
    return new AdaptiveInterviewTurnResponse(
        turn.turnIndex(),
        turn.dimensionOrder(),
        turn.question(),
        turn.answer()
    );
  }
}
