package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.AnswerSources;
import java.util.List;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 以本场原文验证评估提案，定位每条引用后才交给提交事务。 */
@Service
@RequiredArgsConstructor
public class DepthAssessmentAgent {
  private static final int MAX_RATIONALE_LENGTH = 500;
  private static final int MAX_ANCHOR_LENGTH = 80;
  private static final int MAX_MISSING_POINT_LENGTH = 120;
  private static final double MIN_CONFIDENCE = 0;
  private static final double MAX_CONFIDENCE = 1;

  private final AssessmentProposalGenerator generator;

  public AssessmentDecision assess(AssessmentRequest request, String llmProvider) {
    AssessmentProposal proposal = generator.generate(request, llmProvider);
    validateCompleteness(proposal);
    try {
      return validatedDecision(proposal, request);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, e.getMessage(), e);
    }
  }

  private AssessmentDecision validatedDecision(AssessmentProposal proposal, AssessmentRequest request) {
    var code = request.context().codeTaskContext();
    var sources = new AnswerSources(request.context().answer(), code == null ? null : code.submittedCode());
    validateReview(proposal.codeReview(), code);
    List<SourceQuote> quotes = proposal.evidenceQuotes().stream().map(quote -> resolve(quote, sources)).toList();
    if (proposal.depthLevel() != DepthLevel.L0 && quotes.isEmpty()) {
      throw new IllegalArgumentException("回答深度评估结果不完整");
    }
    List<ProbeGap> gaps = proposal.probeGaps().stream().map(gap -> resolveGap(gap, sources)).toList();
    List<GapResolution> resolved = resolveClosures(proposal.resolvedGaps(), request.context(), sources);
    return new AssessmentDecision(request.sessionId(), request.turnIndex(), proposal.depthLevel(),
        proposal.confidence(), proposal.rationaleSummary().trim(), quotes, gaps, resolved, proposal.codeReview());
  }

  private void validateReview(CodeRepairReview review, AssessmentContext.CodeTaskContext code) {
    if (code == null || code.submittedCode() == null) {
      if (review != null) throw new IllegalArgumentException("文字回答不能生成代码修复评分");
      return;
    }
    if (review == null) throw new IllegalArgumentException("代码改错评估必须包含 codeReview");
    review.validate(code.task());
  }

  private List<GapResolution> resolveClosures(List<GapResolution> resolutions,
      AssessmentContext context, AnswerSources sources) {
    var allowed = context.openGaps().stream().map(gap -> gap.gapId()).toList();
    var seen = new HashSet<Long>();
    return resolutions.stream().map(resolution -> {
      if (resolution == null || !allowed.contains(resolution.gapId()) || !seen.add(resolution.gapId())
          || resolution.reason() == null || resolution.reason().isBlank()
          || resolution.reason().length() > MAX_RATIONALE_LENGTH) {
        throw new IllegalArgumentException("缺口关闭必须引用当前回答并指向开放缺口");
      }
      return new GapResolution(resolution.gapId(), resolve(resolution.evidenceQuote(), sources), resolution.reason());
    }).toList();
  }

  private ProbeGap resolveGap(ProbeGap gap, AnswerSources sources) {
    if (gap == null || gap.anchor() == null || gap.anchor().quote() == null
        || gap.anchor().quote().length() > MAX_ANCHOR_LENGTH || gap.missingPoint() == null
        || gap.missingPoint().isBlank() || gap.missingPoint().length() > MAX_MISSING_POINT_LENGTH) {
      throw new IllegalArgumentException("回答追问点必须包含锚点和缺失点");
    }
    return new ProbeGap(resolve(gap.anchor(), sources), gap.missingPoint());
  }

  private SourceQuote resolve(SourceQuote quote, AnswerSources sources) {
    if (quote == null) throw new IllegalArgumentException("评估引用不能为空");
    return quote.resolve(sources);
  }

  private void validateCompleteness(AssessmentProposal proposal) {
    if (proposal == null || proposal.evidenceQuotes() == null || proposal.probeGaps() == null) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整");
    }
    if (proposal.depthLevel() == null || !Double.isFinite(proposal.confidence())
        || proposal.confidence() < MIN_CONFIDENCE || proposal.confidence() > MAX_CONFIDENCE) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估结果不完整：等级或置信度无效");
    }
    if (proposal.rationaleSummary() == null || proposal.rationaleSummary().isBlank()
        || proposal.rationaleSummary().length() > MAX_RATIONALE_LENGTH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "回答深度评估摘要不完整");
    }
  }
}
