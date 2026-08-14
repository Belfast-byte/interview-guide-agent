package interview.guide.modules.interview.agent.adaptive.assessment;

public record ValidatedAssessmentEvidence(
    EvidenceType type,
    String quote,
    Long toolCallId
) {}
