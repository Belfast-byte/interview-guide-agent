package interview.guide.modules.interview.agent.adaptive.assessment;

public record AssessmentRequest(
    String sessionId,
    int turnIndex,
    AssessmentContext context
) {}
