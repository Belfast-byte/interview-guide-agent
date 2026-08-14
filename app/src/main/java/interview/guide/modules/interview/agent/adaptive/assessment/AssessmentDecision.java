package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

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
