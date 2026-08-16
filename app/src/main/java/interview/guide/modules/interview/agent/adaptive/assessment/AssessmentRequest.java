package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估请求，包含上下文与证据来源。
 */
public record AssessmentRequest(
    String sessionId,
    int turnIndex,
    AssessmentContext context
) {}
