package interview.guide.modules.interview.agent.adaptive.assessment.report;

import java.util.List;

/**
 * 企业视角评估报告。
 */
public record EnterpriseAssessmentReport(
    String sessionId,
    String candidateId,
    List<ReportDimensionConclusion> dimensionMatrix,
    List<ProjectCodeSourceReference> projectSources,
    String disclaimer
) {}
