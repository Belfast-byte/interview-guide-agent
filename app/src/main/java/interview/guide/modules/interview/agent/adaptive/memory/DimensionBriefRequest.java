package interview.guide.modules.interview.agent.adaptive.memory;

import java.util.List;

/**
 * 维度简报请求。
 */
public record DimensionBriefRequest(
    String sessionId,
    int dimensionOrder,
    String dimension,
    String focus,
    List<DimensionBriefTurn> turns
) {

  public DimensionBriefRequest {
    turns = List.copyOf(turns);
  }
}
