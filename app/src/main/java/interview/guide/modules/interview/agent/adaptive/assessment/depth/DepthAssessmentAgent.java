package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AnswerTextNormalizer;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 深度评估 Agent，按深度量规对候选人回答进行评级并提取证据和追问点。
 */
@Service
@RequiredArgsConstructor
public class DepthAssessmentAgent {

  private static final int MAX_RATIONALE_LENGTH = 500;
  private static final int MAX_ANCHOR_LENGTH = 80;
  private static final int MAX_MISSING_POINT_LENGTH = 120;

  private final AssessmentProposalGenerator generator;

  public AssessmentDecision assess(
      AssessmentRequest request,
      String llmProvider
  ) {
    AssessmentProposal proposal = generator.generate(request, llmProvider);
    validate(proposal, request);
    return new AssessmentDecision(
        request.sessionId(),
        request.turnIndex(),
        proposal.depthLevel(),
        proposal.confidence(),
        proposal.rationaleSummary().trim(),
        proposal.evidenceQuotes(),
        proposal.probeGaps()
    );
  }

  private void validate(AssessmentProposal proposal, AssessmentRequest request) {
    validateCompleteness(proposal);
    validateEvidenceQuotes(proposal);
    validateProbeGaps(proposal.probeGaps(), request.context().answer());
  }

  private void validateCompleteness(AssessmentProposal proposal) {
    if (proposal == null
        || proposal.depthLevel() == null
        || !Double.isFinite(proposal.confidence())
        || proposal.confidence() < 0
        || proposal.confidence() > 1
        || proposal.rationaleSummary() == null
        || proposal.rationaleSummary().isBlank()
        || proposal.rationaleSummary().length() > MAX_RATIONALE_LENGTH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整");
    }
  }

  private void validateEvidenceQuotes(AssessmentProposal proposal) {
    // L0 语义是「无证据」，允许证据引用为空；其余等级必须至少有一条非空引用
    if (proposal.evidenceQuotes().stream().anyMatch(String::isBlank)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整");
    }
    if (proposal.depthLevel() != DepthLevel.L0 && proposal.evidenceQuotes().isEmpty()) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整");
    }
  }

  private void validateProbeGaps(List<ProbeGap> probeGaps, String answer) {
    String normalizedAnswer = AnswerTextNormalizer.normalize(answer);
    for (ProbeGap gap : probeGaps) {
      if (!isWellFormed(gap)) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答追问点必须包含锚点和缺失点");
      }
      if (!normalizedAnswer.contains(AnswerTextNormalizer.normalize(gap.anchor()))) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答追问点锚定内容不存在于回答原文");
      }
    }
  }

  private static boolean isWellFormed(ProbeGap gap) {
    return gap.anchor() != null
        && !gap.anchor().isBlank()
        && gap.anchor().length() <= MAX_ANCHOR_LENGTH
        && gap.missingPoint() != null
        && !gap.missingPoint().isBlank()
        && gap.missingPoint().length() <= MAX_MISSING_POINT_LENGTH;
  }
}
