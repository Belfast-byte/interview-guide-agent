package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估报告证据事实。
 */
public record AssessmentReportEvidenceFacts(
    EvidenceType type,
    int turnIndex,
    String question,
    String answer,
    String quote,
    Long toolCallId,
    String sandboxExecutionId,
    String toolName,
    String toolResultId,
    String toolOutput
) {

  public AssessmentReportEvidenceFacts(
      EvidenceType type,
      int turnIndex,
      String question,
      String answer,
      String quote,
      Long toolCallId,
      String toolName,
      String toolResultId,
      String toolOutput
  ) {
    this(
        type,
        turnIndex,
        question,
        answer,
        quote,
        toolCallId,
        null,
        toolName,
        toolResultId,
        toolOutput
    );
  }
}
