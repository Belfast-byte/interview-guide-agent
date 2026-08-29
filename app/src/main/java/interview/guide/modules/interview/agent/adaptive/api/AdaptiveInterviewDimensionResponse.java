package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView.TargetCoverage;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
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
    TargetWorkStatus status
) {

  static AdaptiveInterviewDimensionResponse from(
      TargetCoverage coverage,
      TargetWorkStatus displayStatus
  ) {
    CapabilityTarget target = coverage.target();
    return new AdaptiveInterviewDimensionResponse(
        target.identity().order(),
        target.identity().dimension(),
        target.identity().focus(),
        target.budget().turnBudget(),
        target.budget().followUpBudget(),
        target.budget().toolBudget(),
        target.depth().expected(),
        target.depth().ceiling(),
        target.evidenceObjectives(),
        coverage.askedTurns(),
        displayStatus
    );
  }
}
