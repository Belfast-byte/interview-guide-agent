package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;

/**
 * 自适应面试轮次响应。
 */
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
