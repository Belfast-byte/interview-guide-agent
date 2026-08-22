package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

/**
 * 评估证据候选，用于后续校验是否可采信。
 */
public record AssessmentEvidenceCandidate(String quote) {

  public static AssessmentEvidenceCandidate quote(String quote) {
    return new AssessmentEvidenceCandidate(quote);
  }
}
