package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

/**
 * 创建 Agent 一次模型调用输出的计划与首题建议。
 */
public record PlanProposal(
    List<DimensionProposal> dimensions,
    InitialQuestionProposal initialQuestion
) {

  public PlanProposal {
    dimensions = List.copyOf(dimensions);
  }

  public PlanProposal(List<DimensionProposal> dimensions) {
    this(dimensions, null);
  }
}
