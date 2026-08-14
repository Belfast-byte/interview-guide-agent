package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

public record DimensionProposal(
    String dimension,
    String focus,
    String focusId,
    int suggestedTurns,
    List<String> suggestedTools,
    String suggestedSkill
) {

  public DimensionProposal {
    suggestedTools = List.copyOf(suggestedTools);
  }
}
