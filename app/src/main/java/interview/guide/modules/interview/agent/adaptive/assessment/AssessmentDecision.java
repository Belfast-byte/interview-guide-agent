package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

/**
 * 评估决策结果，包含深度等级、证据和建议动作。
 */
public record AssessmentDecision(
    String sessionId,
    int turnIndex,
    DepthLevel depthLevel,
    double confidence,
    String rationaleSummary,
    boolean recommendSwitchQuestion,
    List<String> evidenceQuotes
) {

  public AssessmentDecision {
    evidenceQuotes = List.copyOf(evidenceQuotes);
  }
}
