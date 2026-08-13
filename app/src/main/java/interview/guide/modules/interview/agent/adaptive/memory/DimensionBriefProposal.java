package interview.guide.modules.interview.agent.adaptive.memory;

import java.util.List;

public record DimensionBriefProposal(
    String keyFindings,
    List<Integer> turnIndexes
) {}
