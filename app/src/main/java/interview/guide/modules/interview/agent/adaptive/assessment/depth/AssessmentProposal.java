package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import io.swagger.v3.oas.annotations.media.Schema;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeRepairReview;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.util.List;

/**
 * 评估 Agent 输出的原始建议。
 */
public record AssessmentProposal(
    DepthLevel depthLevel,
    double confidence,
    String rationaleSummary,
    List<SourceQuote> evidenceQuotes,
    List<ProbeGap> probeGaps,
    List<GapResolution> resolvedGaps,
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) CodeRepairReview codeReview
) {

  public AssessmentProposal {
    evidenceQuotes = immutable(evidenceQuotes);
    probeGaps = immutable(probeGaps);
    resolvedGaps = resolvedGaps == null ? List.of() : immutable(resolvedGaps);
  }

  private static <T> List<T> immutable(List<T> values) {
    return values == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
  }

  public AssessmentProposal(DepthLevel depthLevel, double confidence, String rationaleSummary, List<SourceQuote> evidenceQuotes, List<ProbeGap> probeGaps) {
    this(depthLevel, confidence, rationaleSummary, evidenceQuotes, probeGaps, List.of(), null);
  }

  public AssessmentProposal(
      DepthLevel depthLevel,
      double confidence,
      String rationaleSummary,
      List<SourceQuote> evidenceQuotes
  ) {
    this(depthLevel, confidence, rationaleSummary, evidenceQuotes, List.of());
  }
}
