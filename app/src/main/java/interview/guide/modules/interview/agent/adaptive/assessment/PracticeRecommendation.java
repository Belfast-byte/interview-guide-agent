package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 练习推荐结果。
 */
public record PracticeRecommendation(
    int dimensionOrder,
    String dimension,
    DepthLevel demonstratedLevel,
    String questionSourceId,
    String questionDifficulty,
    String question,
    PracticeStatus status
) {}
