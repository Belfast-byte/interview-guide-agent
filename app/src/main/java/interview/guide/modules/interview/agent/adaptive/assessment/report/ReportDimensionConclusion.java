package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.List;

/**
 * 报告维度结论。
 */
public record ReportDimensionConclusion(
    int order,
    String dimension,
    String focus,
    DepthLevel depthLevel,
    double confidence,
    String rationale,
    List<ReportEvidenceReference> evidences
) {}
