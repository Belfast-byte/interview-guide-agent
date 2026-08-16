package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 练习题目事实。
 */
public record PracticeQuestionFacts(
    int turnIndex,
    String sourceId,
    String difficulty
) {}
