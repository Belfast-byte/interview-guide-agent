package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record EnterpriseAssessmentReport(
    String sessionId,
    String candidateId,
    List<ReportDimensionConclusion> dimensionMatrix,
    String disclaimer
) {}
