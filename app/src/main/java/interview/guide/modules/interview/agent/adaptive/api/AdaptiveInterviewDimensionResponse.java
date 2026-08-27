package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;

/**
 * 自适应面试维度响应。
 */
public record AdaptiveInterviewDimensionResponse(
    int order,
    String dimension,
    String focus,
    int allocatedTurns,
    int followUpBudget,
    int toolBudget,
    DepthLevel expectedDepth,
    DepthLevel depthCeiling,
    List<CapabilityTarget.EvidenceObjective> evidenceObjectives,
    int completedTurns,
    PlanDimensionStatus status
) {

  static AdaptiveInterviewDimensionResponse from(PlannedDimension dimension) {
    return new AdaptiveInterviewDimensionResponse(
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        dimension.allocatedTurns(),
        dimension.followUpBudget(),
        dimension.toolBudget(),
        dimension.expectedDepth(),
        dimension.depthCeiling(),
        dimension.evidenceObjectives(),
        dimension.completedTurns(),
        dimension.status()
    );
  }
}
