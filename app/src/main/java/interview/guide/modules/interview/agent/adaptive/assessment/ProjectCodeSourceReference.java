package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.core.CodeFactUsage;

/**
 * 项目代码来源引用。
 */
public record ProjectCodeSourceReference(
    int turnIndex,
    String question,
    String sourceId,
    String anchor,
    CodeFactUsage usage
) {}
