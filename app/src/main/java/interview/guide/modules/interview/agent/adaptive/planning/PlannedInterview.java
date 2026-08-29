package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import java.util.List;

/**
 * 已创建的自适应面试聚合，包含会话、计划和历史。
 */
public record PlannedInterview(
    AdaptiveInterviewHistory history,
    InterviewPlan plan,
    InterviewWorkState workState,
    CoverageView coverage,
    List<DimensionBrief> dimensionBriefs
) {

  public PlannedInterview {
    dimensionBriefs = List.copyOf(dimensionBriefs);
  }

  public PlannedInterview(
      AdaptiveInterviewHistory history,
      InterviewPlan plan,
      InterviewWorkState workState,
      List<DimensionBrief> dimensionBriefs
  ) {
    this(history, plan, workState, minimumCoverage(history, plan), dimensionBriefs);
  }

  private static CoverageView minimumCoverage(
      AdaptiveInterviewHistory history,
      InterviewPlan plan
  ) {
    return CoverageProjector.project(new CoverageFacts(
        plan.maxTurns(),
        plan.dimensions().stream().map(PlannedDimension::target).toList(),
        history.turns().stream()
            .filter(turn -> turn.dimensionOrder() != null)
            .map(turn -> new CoverageFacts.TurnFact(
                turn.turnIndex(), CoverageProjector.targetId(turn.dimensionOrder())
            ))
            .toList(),
        List.of(),
        List.of(),
        List.of()
    ));
  }
}
