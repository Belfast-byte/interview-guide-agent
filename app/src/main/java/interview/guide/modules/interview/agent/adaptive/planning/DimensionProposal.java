package interview.guide.modules.interview.agent.adaptive.planning;

/**
 * 规划器提出的单个维度建议。
 */
public record DimensionProposal(
    String dimension,
    String focus,
    String focusId,
    int suggestedTurns,
    String suggestedSkill
) {}
