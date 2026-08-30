package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
/**
 * 报告证据引用。
 */
public record ReportEvidenceReference(
    EvidenceType type,
    int turnIndex,
    String question,
    String answer,
    String quote,
    ReportToolResult toolResult
) {

  static ReportEvidenceReference from(AssessmentReportEvidenceFacts facts) {
    ReportToolResult toolResult = facts.type() == EvidenceType.TOOL_RESULT
        ? new ReportToolResult(
            facts.sandboxExecutionId(),
            facts.toolName(),
            facts.toolResultId(),
            facts.toolOutput()
        )
        : null;
    return new ReportEvidenceReference(
        facts.type(),
        facts.turnIndex(),
        facts.question(),
        facts.answer(),
        facts.quote(),
        toolResult
    );
  }
}
