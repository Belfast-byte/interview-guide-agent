package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import java.util.List;

public record PlannedInterview(
    AdaptiveInterviewHistory history,
    InterviewPlan plan,
    List<DimensionBrief> dimensionBriefs
) {

  public PlannedInterview {
    dimensionBriefs = List.copyOf(dimensionBriefs);
  }
}
