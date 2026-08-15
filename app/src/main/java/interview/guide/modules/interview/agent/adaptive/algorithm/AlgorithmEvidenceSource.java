package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.util.Map;
import java.util.Set;

public interface AlgorithmEvidenceSource {

  Map<String, String> findCandidateEvidenceIds(
      String sessionId,
      int turnIndex,
      Set<String> resultIds
  );

  Map<String, String> findCandidateEvidenceIds(String sessionId, int turnIndex);

  Set<Integer> findCandidateTurnIndexes(String sessionId);

  Map<String, AlgorithmEvidence> findEvidence(Set<String> executionIds);
}
