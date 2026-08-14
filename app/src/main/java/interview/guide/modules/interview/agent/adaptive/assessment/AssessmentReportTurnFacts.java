package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record AssessmentReportTurnFacts(
    int turnIndex,
    DepthLevel depthLevel,
    double confidence,
    String rationale,
    List<AssessmentReportEvidenceFacts> evidences
) {}
