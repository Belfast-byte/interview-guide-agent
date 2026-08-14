package interview.guide.modules.interview.agent.adaptive.assessment;

public record AssessmentReportEvidenceFacts(
    EvidenceType type,
    int turnIndex,
    String question,
    String answer,
    String quote,
    Long toolCallId,
    String toolName,
    String toolResultId,
    String toolOutput
) {}
