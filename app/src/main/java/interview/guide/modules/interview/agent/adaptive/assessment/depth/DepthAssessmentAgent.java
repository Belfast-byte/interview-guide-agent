package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AnswerTextNormalizer;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 深度评估 Agent，按深度量规对候选人回答进行评级并提取证据和追问点。
 * 模型输出校验失败时让模型重写一次；重写后追问点仍锚定失败时降级丢弃非法追问点
 * （与证据引用的丢弃策略一致），结构性不完整仍抛出业务异常；模型超时不重写，
 * 避免把单次 deadline 放大成两倍让前端先超时。追问点超量截断而非拒绝。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepthAssessmentAgent {

  private static final int MAX_RATIONALE_LENGTH = 500;
  private static final int MAX_PROBE_GAPS = 2;
  private static final int MAX_ANCHOR_LENGTH = 80;
  private static final int MAX_MISSING_POINT_LENGTH = 120;

  private final AssessmentProposalGenerator generator;

  public AssessmentDecision assess(
      AssessmentRequest request,
      String llmProvider
  ) {
    AssessmentProposal proposal = truncateProbeGaps(generator.generate(request, llmProvider));
    try {
      validate(proposal, request);
    } catch (BusinessException e) {
      // 超时等非校验类失败不重试：重试会把单次 deadline 放大成两倍，前端先超时
      if (e.getCode() != ErrorCode.AI_SERVICE_ERROR.getCode()) {
        throw e;
      }
      proposal = truncateProbeGaps(generator.generate(request, llmProvider));
      try {
        validate(proposal, request);
      } catch (BusinessException retryFailure) {
        proposal = dropInvalidProbeGaps(proposal, request, retryFailure);
      }
    }
    return new AssessmentDecision(
        request.sessionId(),
        request.turnIndex(),
        proposal.depthLevel(),
        proposal.confidence(),
        proposal.rationaleSummary().trim(),
        proposal.recommendSwitchQuestion(),
        proposal.evidenceQuotes(),
        proposal.probeGaps()
    );
  }

  private AssessmentProposal truncateProbeGaps(AssessmentProposal proposal) {
    if (proposal == null || proposal.probeGaps().size() <= MAX_PROBE_GAPS) {
      return proposal;
    }
    return new AssessmentProposal(
        proposal.depthLevel(),
        proposal.confidence(),
        proposal.rationaleSummary(),
        proposal.recommendSwitchQuestion(),
        proposal.evidenceQuotes(),
        proposal.probeGaps().subList(0, MAX_PROBE_GAPS)
    );
  }

  private void validate(AssessmentProposal proposal, AssessmentRequest request) {
    validateCompleteness(proposal);
    validateEvidenceQuotes(proposal);
    validateProbeGaps(proposal.probeGaps(), request.context().answer());
  }

  /**
   * 重写后仍校验失败时的降级路径：结构性不完整或证据引用非法不可降级，原样抛出；
   * 仅追问点锚定失败时丢弃无法逐字命中回答原文的追问点后接受结果。
   */
  private AssessmentProposal dropInvalidProbeGaps(
      AssessmentProposal proposal,
      AssessmentRequest request,
      BusinessException retryFailure
  ) {
    try {
      validateCompleteness(proposal);
      validateEvidenceQuotes(proposal);
    } catch (BusinessException structural) {
      retryFailure.addSuppressed(structural);
      throw retryFailure;
    }
    String normalizedAnswer = AnswerTextNormalizer.normalize(request.context().answer());
    List<ProbeGap> validGaps = proposal.probeGaps().stream()
        .filter(DepthAssessmentAgent::isWellFormed)
        .filter(gap -> normalizedAnswer.contains(AnswerTextNormalizer.normalize(gap.anchor())))
        .toList();
    log.warn(
        "回答追问点重写后仍锚定失败，降级丢弃非法追问点: sessionId={}, turnIndex={}, 丢弃 {} 条",
        request.sessionId(),
        request.turnIndex(),
        proposal.probeGaps().size() - validGaps.size()
    );
    return new AssessmentProposal(
        proposal.depthLevel(),
        proposal.confidence(),
        proposal.rationaleSummary(),
        proposal.recommendSwitchQuestion(),
        proposal.evidenceQuotes(),
        validGaps
    );
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
