package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record AssessmentProposal(
    DepthLevel depthLevel,
    double confidence,
    String rationaleSummary,
    boolean recommendSwitchQuestion,
    List<String> evidenceQuotes
) {}
