package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;

public record McpInterviewDimensionResponse(
    int order,
    String dimension,
    String focus,
    int allocatedTurns,
    int completedTurns,
    PlanDimensionStatus status
) {

  static McpInterviewDimensionResponse from(PlannedDimension dimension) {
    return new McpInterviewDimensionResponse(
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        dimension.allocatedTurns(),
        dimension.completedTurns(),
        dimension.status()
    );
  }
}
