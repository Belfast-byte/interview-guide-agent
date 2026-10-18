package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote.AnswerSources;
import interview.guide.modules.interview.agent.adaptive.observability.AssessmentObservationLog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 验证正式评判契约；引用仅作辅助，无法定位时记录观察而不阻断评判。 */
@Service
@RequiredArgsConstructor
public class DepthAssessmentAgent {
  private static final int MAX_RATIONALE_LENGTH = 500;
  private static final int MAX_MISSING_POINT_LENGTH = 120;
  private static final double MIN_CONFIDENCE = 0;
  private static final double MAX_CONFIDENCE = 1;

  private final AssessmentProposalGenerator generator;

  public AssessmentDecision assess(AssessmentRequest request, String llmProvider) {
    String observationId = UUID.randomUUID().toString();
    var references = new ArrayList<AssessmentObservationLog.ReferenceObservation>();
    AssessmentProposal proposal = null;
    try {
      proposal = generator.generate(request, llmProvider);
      validateCompleteness(proposal);
      AssessmentDecision decision = validatedDecision(proposal, request, references, observationId);
      AssessmentObservationLog.assessed(request, llmProvider, observationId, proposal, decision, references, null);
      return decision;
    } catch (RuntimeException error) {
      AssessmentObservationLog.assessed(request, llmProvider, observationId, proposal, null, references, error);
      if (error instanceof IllegalArgumentException) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, error.getMessage(), error);
      }
      throw error;
    }
  }

  private AssessmentDecision validatedDecision(AssessmentProposal proposal, AssessmentRequest request,
      List<AssessmentObservationLog.ReferenceObservation> references, String observationId) {
    var code = request.context().codeTaskContext();
    var sources = new AnswerSources(request.context().answer(), code == null ? null : code.submittedCode());
    validateReview(proposal.codeReview(), code);
    List<SourceQuote> quotes = new ArrayList<>();
    for (int i = 0; i < proposal.evidenceQuotes().size(); i++) {
      var quote = resolve(proposal.evidenceQuotes().get(i), sources, "evidenceQuotes[" + i + "]", references);
      if (quote != null) quotes.add(quote);
    }
    List<ProbeGap> gaps = new ArrayList<>();
    for (int i = 0; i < proposal.probeGaps().size(); i++) {
      var gap = proposal.probeGaps().get(i);
      if (gap == null || gap.missingPoint() == null || gap.missingPoint().isBlank()
          || gap.missingPoint().length() > MAX_MISSING_POINT_LENGTH) {
        throw new IllegalArgumentException("回答追问点必须包含缺失点");
      }
      gaps.add(new ProbeGap(resolve(gap.anchor(), sources, "probeGaps[" + i + "].anchor", references), gap.missingPoint()));
    }
    List<GapResolution> resolved = resolveClosures(proposal.resolvedGaps(), request.context(), sources, references);
    return new AssessmentDecision(request.sessionId(), request.turnIndex(), proposal.depthLevel(),
        proposal.confidence(), proposal.rationaleSummary().trim(), quotes, gaps, resolved, proposal.codeReview(), observationId);
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
      AssessmentContext context, AnswerSources sources,
      List<AssessmentObservationLog.ReferenceObservation> references) {
    var allowed = context.openGaps().stream().map(gap -> gap.gapId()).toList();
    var seen = new HashSet<Long>();
    return resolutions.stream().map(resolution -> {
      if (resolution == null || !allowed.contains(resolution.gapId()) || !seen.add(resolution.gapId())
          || resolution.reason() == null || resolution.reason().isBlank()
          || resolution.reason().length() > MAX_RATIONALE_LENGTH) {
        throw new IllegalArgumentException("缺口关闭必须包含理由并指向开放缺口");
      }
      return new GapResolution(resolution.gapId(), resolve(resolution.evidenceQuote(), sources,
          "resolvedGaps[" + resolution.gapId() + "].evidenceQuote", references), resolution.reason());
    }).toList();
  }

  private SourceQuote resolve(SourceQuote quote, AnswerSources sources, String path,
      List<AssessmentObservationLog.ReferenceObservation> references) {
    if (quote == null) {
      references.add(new AssessmentObservationLog.ReferenceObservation(path, null, "ABSENT", null));
      return null;
    }
    try {
      var resolved = quote.resolve(sources);
      references.add(new AssessmentObservationLog.ReferenceObservation(path, quote, "VERIFIED", null));
      return resolved;
    } catch (IllegalArgumentException error) {
      references.add(new AssessmentObservationLog.ReferenceObservation(path, quote, "UNVERIFIED", error.getMessage()));
      return null;
    }
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
