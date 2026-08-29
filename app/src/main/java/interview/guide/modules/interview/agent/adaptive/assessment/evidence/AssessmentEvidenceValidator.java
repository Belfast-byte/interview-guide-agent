package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 评估证据校验器，校验逐字引用是否真实存在于回答原文中。
 * 匹配前对全半角和空白做归一化；引用不命中时明确拒绝整份正式提案。
 */
@Service
public class AssessmentEvidenceValidator {

  public List<ValidatedAssessmentEvidence> validate(
      String sessionId,
      int turnIndex,
      String answer,
      List<AssessmentEvidenceCandidate> candidates
  ) {
    String normalizedAnswer = AnswerTextNormalizer.normalize(answer);
    List<ValidatedAssessmentEvidence> validated = new ArrayList<>();
    for (AssessmentEvidenceCandidate candidate : candidates.stream().distinct().toList()) {
      String quote = candidate.quote();
      if (quote == null || quote.isBlank()
          || !normalizedAnswer.contains(AnswerTextNormalizer.normalize(quote))) {
        throw new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "评估证据引用未命中回答原文: sessionId=%s, turnIndex=%d"
                .formatted(sessionId, turnIndex)
        );
      }
      validated.add(new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote, null));
    }
    return List.copyOf(validated);
  }
}
