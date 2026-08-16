package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

/**
 * 维度简报值对象，汇总某考察维度的核心信息供面试官参考。
 */
public record DimensionBrief(
    String sessionId,
    int dimensionOrder,
    String dimension,
    String focus,
    String keyFindings,
    List<Integer> turnIndexes
) {

  public DimensionBrief {
    turnIndexes = List.copyOf(turnIndexes);
  }
}
