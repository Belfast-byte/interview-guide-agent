package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record ReportDimensionConclusion(
    int order,
    String dimension,
    String focus,
    DepthLevel depthLevel,
    double confidence,
    String rationale,
    List<ReportEvidenceReference> evidences
) {}
