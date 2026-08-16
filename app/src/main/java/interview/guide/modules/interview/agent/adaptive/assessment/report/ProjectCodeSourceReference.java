package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;

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
