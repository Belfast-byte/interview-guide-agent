package interview.guide.modules.interview.agent.adaptive.core.context;

/** 正式面试官可见的中性历史题目提示，不包含旧答案或评级。 */
public record QuestionRecallHint(
    String question,
    String evidenceObjective,
    String revalidationNeed
) {}
