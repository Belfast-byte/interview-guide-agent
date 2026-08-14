package interview.guide.modules.interview.agent.adaptive.assessment;

public record AssessmentBackfillTurn(
    String sessionId,
    int turnIndex,
    int dimensionOrder,
    String dimension,
    String focus,
    String question,
    String answer,
    String llmProvider
) {}
