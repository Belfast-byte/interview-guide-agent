package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import java.util.List;

/**
 * 评估报告全部事实。
 */
public record AssessmentReportFacts(
    String sessionId,
    String candidateId,
    AdaptiveSessionStatus status,
    List<AssessmentReportDimensionFacts> dimensions,
    List<PracticeRecommendation> practiceRecommendations,
    List<ProjectCodeSourceReference> projectSources
) {}
