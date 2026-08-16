package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import java.util.List;

/**
 * 已创建的自适应面试聚合，包含会话、计划和历史。
 */
public record PlannedInterview(
    AdaptiveInterviewHistory history,
    InterviewPlan plan,
    List<DimensionBrief> dimensionBriefs
) {

  public PlannedInterview {
    dimensionBriefs = List.copyOf(dimensionBriefs);
  }
}
