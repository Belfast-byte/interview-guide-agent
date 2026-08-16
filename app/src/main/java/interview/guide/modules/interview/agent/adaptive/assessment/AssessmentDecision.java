package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.core.ProbeGap;
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
    boolean recommendSwitchQuestion,
    List<String> evidenceQuotes,
    List<ProbeGap> probeGaps
) {

  public AssessmentDecision {
    evidenceQuotes = List.copyOf(evidenceQuotes);
    probeGaps = List.copyOf(probeGaps);
  }

  public AssessmentDecision(
      String sessionId,
      int turnIndex,
      DepthLevel depthLevel,
      double confidence,
      String rationaleSummary,
      boolean recommendSwitchQuestion,
      List<String> evidenceQuotes
  ) {
    this(sessionId, turnIndex, depthLevel, confidence, rationaleSummary,
        recommendSwitchQuestion, evidenceQuotes, List.of());
  }
}
