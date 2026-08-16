package interview.guide.modules.interview.agent.model;

import interview.guide.modules.interview.agent.runtime.Turn;

/**
 * Agent 面试轮次响应。
 */
public record AgentInterviewTurnResponse(
    int turnNumber,
    String question,
    String answer
) {

  public static AgentInterviewTurnResponse from(Turn turn) {
    return new AgentInterviewTurnResponse(
        turn.turnNumber(),
        turn.question(),
        turn.answer()
    );
  }
}
