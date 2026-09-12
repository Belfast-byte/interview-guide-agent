package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.AnswerSources;
import java.util.List;
import org.springframework.stereotype.Service;

/** 逐字验证本轮答案与代码证据，不归一化代码空白或伪造位置。 */
@Service
public class AssessmentEvidenceValidator {
  public List<ValidatedAssessmentEvidence> validate(AnswerSources sources,
      List<AssessmentEvidenceCandidate> candidates) {
    try {
      return candidates.stream().map(candidate -> validateQuote(candidate, sources)).distinct().toList();
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评估证据引用未命中回答原文: " + e.getMessage(), e);
    }
  }

  private ValidatedAssessmentEvidence validateQuote(AssessmentEvidenceCandidate candidate, AnswerSources sources) {
    if (candidate == null || candidate.quote() == null) throw new IllegalArgumentException("引用不能为空");
    var quote = candidate.quote().resolve(sources);
    return new ValidatedAssessmentEvidence(EvidenceType.QUOTE, quote.quote(), null, quote.locator());
  }
}
