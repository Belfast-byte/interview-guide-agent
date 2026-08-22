package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 评估证据校验器，校验逐字引用是否真实存在于回答原文中。
 */
@Service
public class AssessmentEvidenceValidator {

  public List<ValidatedAssessmentEvidence> validate(
      String sessionId,
      int turnIndex,
      String answer,
      List<AssessmentEvidenceCandidate> candidates
  ) {
    return candidates.stream()
        .distinct()
        .map(candidate -> validateQuote(candidate.quote(), answer))
        .toList();
  }

  private ValidatedAssessmentEvidence validateQuote(String quote, String answer) {
    if (quote == null || quote.isBlank() || !answer.contains(quote)) {
      throw invalidEvidence();
    }
    return new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote, null);
  }

  private BusinessException invalidEvidence() {
    return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评估证据无法追溯");
  }
}
