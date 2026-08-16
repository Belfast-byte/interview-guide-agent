package interview.guide.modules.interview.agent.adaptive.memory;

import java.util.List;

/**
 * 维度简报建议。
 */
public record DimensionBriefProposal(
    String keyFindings,
    List<Integer> turnIndexes
) {}
