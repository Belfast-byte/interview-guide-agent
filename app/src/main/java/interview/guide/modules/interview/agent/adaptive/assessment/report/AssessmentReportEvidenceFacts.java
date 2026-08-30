package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
/**
 * 评估报告证据事实。
 */
public record AssessmentReportEvidenceFacts(
    EvidenceType type,
    int turnIndex,
    String question,
    String answer,
    String quote,
    String sandboxExecutionId,
    String toolName,
    String toolResultId,
    String toolOutput
) {}
