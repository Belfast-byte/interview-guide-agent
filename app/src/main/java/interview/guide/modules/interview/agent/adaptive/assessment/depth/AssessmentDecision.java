package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.util.List;

/**
 * 评估决策结果，包含深度等级、证据、建议动作和追问点。
 */
public record AssessmentDecision(
    String sessionId,
    int turnIndex,
    DepthLevel depthLevel,
    double confidence,
    String rationaleSummary,
    List<String> evidenceQuotes,
    List<ProbeGap> probeGaps,
    List<GapResolution> resolvedGaps
) {

  public AssessmentDecision {
    evidenceQuotes = List.copyOf(evidenceQuotes);
    probeGaps = List.copyOf(probeGaps);
    resolvedGaps = resolvedGaps == null ? List.of() : List.copyOf(resolvedGaps);
  }

  public AssessmentDecision(String sessionId, int turnIndex, DepthLevel depthLevel, double confidence, String rationaleSummary, List<String> evidenceQuotes, List<ProbeGap> probeGaps) {
    this(sessionId, turnIndex, depthLevel, confidence, rationaleSummary, evidenceQuotes, probeGaps, List.of());
  }

  public AssessmentDecision(
      String sessionId,
      int turnIndex,
      DepthLevel depthLevel,
      double confidence,
      String rationaleSummary,
      List<String> evidenceQuotes
  ) {
    this(sessionId, turnIndex, depthLevel, confidence, rationaleSummary,
        evidenceQuotes, List.of());
  }
}
