package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

/**
 * 候选人评估报告。
 */
public record CandidateAssessmentReport(
    String sessionId,
    List<ReportDimensionConclusion> dimensions,
    List<CandidateWeakPoint> weakPoints,
    List<PracticeRecommendation> practiceRecommendations,
    List<ProjectCodeSourceReference> projectSources
) {}
