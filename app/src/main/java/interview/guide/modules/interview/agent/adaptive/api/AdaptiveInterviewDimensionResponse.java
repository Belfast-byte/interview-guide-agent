package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;

/**
 * 自适应面试维度响应。
 */
public record AdaptiveInterviewDimensionResponse(
    int order,
    String dimension,
    String focus,
    int allocatedTurns,
    int completedTurns,
    PlanDimensionStatus status
) {

  static AdaptiveInterviewDimensionResponse from(PlannedDimension dimension) {
    return new AdaptiveInterviewDimensionResponse(
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        dimension.allocatedTurns(),
        dimension.completedTurns(),
        dimension.status()
    );
  }
}
