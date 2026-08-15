package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public record CandidateAssessmentReport(
    String sessionId,
    List<ReportDimensionConclusion> dimensions,
    List<CandidateWeakPoint> weakPoints,
    List<PracticeRecommendation> practiceRecommendations,
    List<ProjectCodeSourceReference> projectSources
) {}
