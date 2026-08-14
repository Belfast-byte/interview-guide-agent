package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record AssessmentReportDimensionFacts(
    int order,
    String dimension,
    String focus,
    List<AssessmentReportTurnFacts> assessments
) {}
