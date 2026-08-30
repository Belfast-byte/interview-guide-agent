package interview.guide.modules.interview.agent.adaptive.core.action;

/**
 * 题目来源值对象，记录题目出处与可信度信息。
 */
public record QuestionProvenance(
    String stableId,
    String difficulty
) {}
