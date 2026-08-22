package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;

/**
 * MCP 面试状态响应。
 */
public record McpInterviewStatusResponse(
    String sessionId,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String currentQuestion
) {

  static McpInterviewStatusResponse from(PlannedInterview interview) {
    var history = interview.history();
    String question = history.session().status() == AdaptiveSessionStatus.COMPLETED
        ? null
        : history.turns().getLast().question();
    return new McpInterviewStatusResponse(
        history.session().id(),
        history.session().status(),
        history.session().currentTurn(),
        history.session().maxTurns(),
        question
    );
  }
}
