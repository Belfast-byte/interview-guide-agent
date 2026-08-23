package interview.guide.modules.interview.agent.adaptive.assessment.practice;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
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
