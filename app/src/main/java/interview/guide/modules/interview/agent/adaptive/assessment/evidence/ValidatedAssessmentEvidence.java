package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

/**
 * 已通过校验的评估证据。
 */
public record ValidatedAssessmentEvidence(
    EvidenceType type,
    String quote,
    String sandboxExecutionId
) {}
