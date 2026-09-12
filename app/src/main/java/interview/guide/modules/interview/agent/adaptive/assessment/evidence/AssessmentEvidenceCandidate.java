package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;

/** 待验证的有来源引用。 */
public record AssessmentEvidenceCandidate(SourceQuote quote) {
  public static AssessmentEvidenceCandidate quote(SourceQuote quote) {
    return new AssessmentEvidenceCandidate(quote);
  }
}
