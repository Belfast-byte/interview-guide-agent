package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评估证据校验器，校验逐字引用是否真实存在于回答原文中。
 * 匹配前对全半角和空白做归一化；单条引用不命中时丢弃该条并记录日志，不再整轮失败。
 */
@Service
@Slf4j
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
        log.warn("评估证据引用未命中回答原文，已丢弃: sessionId={}, turnIndex={}",
            sessionId, turnIndex);
        continue;
      }
      validated.add(new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote, null));
    }
    return List.copyOf(validated);
  }
}
