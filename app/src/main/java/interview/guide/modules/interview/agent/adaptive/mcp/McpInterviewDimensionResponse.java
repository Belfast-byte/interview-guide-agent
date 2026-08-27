package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;

/**
 * MCP 面试维度响应。
 */
public record McpInterviewDimensionResponse(
    int order,
    String dimension,
    String focus,
    int allocatedTurns,
    int completedTurns,
    TargetWorkStatus status
) {

  static McpInterviewDimensionResponse from(TargetWorkState state) {
    var target = state.target();
    return new McpInterviewDimensionResponse(
        target.identity().order(),
        target.identity().dimension(),
        target.identity().focus(),
        target.budget().turnBudget(),
        target.budget().turnBudget() - state.remainingBudget().turns(),
        state.status()
    );
  }
}
