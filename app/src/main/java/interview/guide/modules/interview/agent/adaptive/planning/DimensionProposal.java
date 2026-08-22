package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

/**
 * 规划器提出的单个维度建议。
 */
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
