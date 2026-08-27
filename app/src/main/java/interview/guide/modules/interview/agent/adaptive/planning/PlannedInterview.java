package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import java.util.List;

/**
 * 已创建的自适应面试聚合，包含会话、计划和历史。
 */
public record PlannedInterview(
    AdaptiveInterviewHistory history,
    InterviewPlan plan,
    InterviewWorkState workState,
    List<DimensionBrief> dimensionBriefs
) {

  public PlannedInterview {
    dimensionBriefs = List.copyOf(dimensionBriefs);
  }
}
