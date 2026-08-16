package interview.guide.modules.interview.agent.adaptive.algorithm;

/**
 * 算法评估指标来源接口。
 */
public interface AlgorithmAssessmentMetricsSource {

  boolean hasActiveJudging(String sessionId);

  long countAssessmentsWithValidResults();

  long countAssessmentsWithSandboxEvidence();

  long countReviewRequiredAssessments();
}
