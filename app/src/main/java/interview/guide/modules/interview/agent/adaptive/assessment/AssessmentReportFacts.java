package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import java.util.List;

public record AssessmentReportFacts(
    String sessionId,
    String candidateId,
    AdaptiveSessionStatus status,
    List<AssessmentReportDimensionFacts> dimensions
) {}
