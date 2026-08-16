package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估证据候选，用于后续校验是否可采信。
 */
public record AssessmentEvidenceCandidate(
    EvidenceType type,
    String quote,
    String toolResultId
) {

  public static AssessmentEvidenceCandidate quote(String quote) {
    return new AssessmentEvidenceCandidate(EvidenceType.QUOTE, quote, null);
  }

  public static AssessmentEvidenceCandidate toolResult(String toolResultId) {
    return new AssessmentEvidenceCandidate(EvidenceType.TOOL_RESULT, null, toolResultId);
  }
}
