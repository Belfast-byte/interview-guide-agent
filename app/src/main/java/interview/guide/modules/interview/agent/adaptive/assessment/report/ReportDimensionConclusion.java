package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import java.util.List;

/**
 * 报告维度结论。未考察时 depthLevel 与 confidence 为 null，不能用 L0 或 0 替代。
 */
public record ReportDimensionConclusion(
    int order,
    String dimension,
    String focus,
    DepthLevel depthLevel,
    Double confidence,
    String rationale,
    List<ReportEvidenceReference> evidences
) {}
