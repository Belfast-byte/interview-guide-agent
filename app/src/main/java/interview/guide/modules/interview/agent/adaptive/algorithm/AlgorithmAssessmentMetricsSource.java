package interview.guide.modules.interview.agent.adaptive.algorithm;

public interface AlgorithmAssessmentMetricsSource {

  boolean hasActiveJudging(String sessionId);

  long countAssessmentsWithValidResults();

  long countAssessmentsWithSandboxEvidence();

  long countReviewRequiredAssessments();
}
