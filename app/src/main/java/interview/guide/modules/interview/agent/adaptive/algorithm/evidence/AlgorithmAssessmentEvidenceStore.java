package interview.guide.modules.interview.agent.adaptive.algorithm.evidence;

/**
 * 算法评估证据存储接口。
 */
public interface AlgorithmAssessmentEvidenceStore {

  boolean attach(String sessionId, int turnIndex, String executionId);
}
