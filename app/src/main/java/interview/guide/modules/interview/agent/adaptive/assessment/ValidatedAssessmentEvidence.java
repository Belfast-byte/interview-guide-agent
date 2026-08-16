package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 已通过校验的评估证据。
 */
public record ValidatedAssessmentEvidence(
    EvidenceType type,
    String quote,
    Long toolCallId,
    String sandboxExecutionId
) {

  public ValidatedAssessmentEvidence(
      EvidenceType type,
      String quote,
      Long toolCallId
  ) {
    this(type, quote, toolCallId, null);
  }
}
