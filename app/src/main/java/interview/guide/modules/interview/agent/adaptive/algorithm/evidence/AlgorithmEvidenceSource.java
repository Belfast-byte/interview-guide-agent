package interview.guide.modules.interview.agent.adaptive.algorithm.evidence;

import java.util.Map;
import java.util.Set;

/**
 * 算法证据来源接口。
 */
public interface AlgorithmEvidenceSource {

  Set<String> findCandidateEvidenceIds(String sessionId, int turnIndex);

  Map<String, AlgorithmEvidence> findEvidence(Set<String> executionIds);
}
