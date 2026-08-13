package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

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
