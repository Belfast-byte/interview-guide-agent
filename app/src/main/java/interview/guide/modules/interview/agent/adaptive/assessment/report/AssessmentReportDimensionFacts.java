package interview.guide.modules.interview.agent.adaptive.assessment.report;

import java.util.List;

/**
 * 评估报告维度事实。
 */
public record AssessmentReportDimensionFacts(
    int order,
    String dimension,
    String focus,
    List<AssessmentReportTurnFacts> assessments
) {}
