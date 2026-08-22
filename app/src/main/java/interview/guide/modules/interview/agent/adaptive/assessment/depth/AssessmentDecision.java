package interview.guide.modules.interview.agent.adaptive.assessment.depth;

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
    boolean recommendSwitchQuestion,
    List<String> evidenceQuotes,
    List<ProbeGap> probeGaps
) {

  public AssessmentDecision {
    evidenceQuotes = List.copyOf(evidenceQuotes);
    probeGaps = List.copyOf(probeGaps);
  }

  /**
   * 评估结论是否建议提前完成当前维度：模型建议换题或已达到 L4（当前维度可提前完成）。
   *
   * @return 是否建议提前完成
   */
  public boolean recommendsEarlyCompletion() {
    return recommendSwitchQuestion || depthLevel == DepthLevel.L4;
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
