package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.core.CodeFactUsage;

public record ProjectCodeSourceReference(
    int turnIndex,
    String question,
    String sourceId,
    String anchor,
    CodeFactUsage usage
) {}
