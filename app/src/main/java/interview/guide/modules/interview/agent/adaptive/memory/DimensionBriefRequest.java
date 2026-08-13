package interview.guide.modules.interview.agent.adaptive.memory;

import java.util.List;

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
