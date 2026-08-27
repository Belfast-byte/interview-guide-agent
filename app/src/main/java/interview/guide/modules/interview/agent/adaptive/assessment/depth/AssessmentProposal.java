package interview.guide.modules.interview.agent.adaptive.assessment.depth;

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
    List<String> evidenceQuotes,
    List<ProbeGap> probeGaps
) {

  public AssessmentProposal {
    evidenceQuotes = List.copyOf(evidenceQuotes);
    probeGaps = List.copyOf(probeGaps);
  }

  public AssessmentProposal(
      DepthLevel depthLevel,
      double confidence,
      String rationaleSummary,
      List<String> evidenceQuotes
  ) {
    this(depthLevel, confidence, rationaleSummary, evidenceQuotes, List.of());
  }
}
