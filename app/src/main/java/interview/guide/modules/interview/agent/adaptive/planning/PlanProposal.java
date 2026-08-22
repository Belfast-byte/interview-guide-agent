package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

/**
 * 规划器输出的完整计划建议。
 */
public record PlanProposal(
    List<DimensionProposal> dimensions
) {

  public PlanProposal {
    dimensions = List.copyOf(dimensions);
  }
}
