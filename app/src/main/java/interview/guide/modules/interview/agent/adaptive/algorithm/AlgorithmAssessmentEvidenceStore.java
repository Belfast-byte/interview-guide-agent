package interview.guide.modules.interview.agent.adaptive.algorithm;

public interface AlgorithmAssessmentEvidenceStore {

  boolean attach(String sessionId, int turnIndex, String executionId);
}
