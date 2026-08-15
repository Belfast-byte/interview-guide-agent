package interview.guide.modules.interview.agent.adaptive.algorithm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlgorithmAssessmentEvidenceService {

  private final AlgorithmEvidenceSource evidenceSource;
  private final AlgorithmAssessmentEvidenceStore evidenceStore;

  public int attachAvailable(String sessionId, int turnIndex) {
    return (int) evidenceSource.findCandidateEvidenceIds(sessionId, turnIndex)
        .keySet().stream()
        .filter(executionId -> evidenceStore.attach(sessionId, turnIndex, executionId))
        .count();
  }

  public int attachAvailable(String sessionId) {
    return evidenceSource.findCandidateTurnIndexes(sessionId).stream()
        .mapToInt(turnIndex -> attachAvailable(sessionId, turnIndex))
        .sum();
  }
}
