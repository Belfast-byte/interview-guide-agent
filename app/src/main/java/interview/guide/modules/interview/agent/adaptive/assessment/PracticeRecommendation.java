package interview.guide.modules.interview.agent.adaptive.assessment;

public record PracticeRecommendation(
    int dimensionOrder,
    String dimension,
    DepthLevel demonstratedLevel,
    String questionSourceId,
    String questionDifficulty,
    String question,
    PracticeStatus status
) {}
