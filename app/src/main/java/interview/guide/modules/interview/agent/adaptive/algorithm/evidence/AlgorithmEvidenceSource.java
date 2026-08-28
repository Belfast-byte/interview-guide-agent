package interview.guide.modules.interview.agent.adaptive.algorithm.evidence;

import java.util.Map;
import java.util.Set;

/**
 * 算法证据来源接口。
 */
public interface AlgorithmEvidenceSource {

  Map<String, AlgorithmEvidence> findEvidence(Set<String> executionIds);
}
