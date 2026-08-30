package interview.guide.modules.interview.agent.adaptive.core.action;

/**
 * 代码题来源信息，记录题目来自哪个项目/文件以及关联锚点。
 */
public record CodeQuestionProvenance(
    String sourceId,
    String anchor,
    CodeFactUsage usage
) {}
