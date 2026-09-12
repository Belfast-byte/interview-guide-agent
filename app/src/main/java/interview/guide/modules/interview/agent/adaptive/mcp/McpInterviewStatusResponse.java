package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.api.AdaptiveInterviewResponse;
import interview.guide.modules.interview.agent.adaptive.api.AdaptiveInterviewResponse.AdaptiveInterviewTurnResponse;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;

/** 保留 MCP 状态字段，通过同一公开投影提供题型、代码与按模式公开的反馈。 */
public record McpInterviewStatusResponse(
    String sessionId,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String currentQuestion,
    AdaptiveInterviewTurnResponse currentTurnDetails,
    List<AdaptiveInterviewTurnResponse> turns
) {
  public McpInterviewStatusResponse { turns = List.copyOf(turns); }

  static McpInterviewStatusResponse from(PlannedInterview interview) {
    AdaptiveInterviewResponse response = AdaptiveInterviewResponse.from(interview);
    AdaptiveInterviewTurnResponse current = response.turns().stream()
        .filter(turn -> turn.turnIndex() == response.currentTurn())
        .findFirst().orElse(null);
    return new McpInterviewStatusResponse(response.sessionId(), response.status(), response.currentTurn(),
        response.maxTurns(), response.currentQuestion(), current, response.turns());
  }
}
