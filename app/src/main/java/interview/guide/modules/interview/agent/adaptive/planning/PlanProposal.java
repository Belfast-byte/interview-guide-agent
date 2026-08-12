package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

public record PlanProposal(
    List<DimensionProposal> dimensions
) {

  public PlanProposal {
    dimensions = List.copyOf(dimensions);
  }
}
