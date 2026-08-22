package interview.guide.modules.interview.agent.adaptive.algorithm.evidence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 算法评估证据服务，将算法评测结果转换为评估证据。
 */
@Service
@RequiredArgsConstructor
public class AlgorithmAssessmentEvidenceService {

  private final AlgorithmEvidenceSource evidenceSource;
  private final AlgorithmAssessmentEvidenceStore evidenceStore;

  public int attachAvailable(String sessionId, int turnIndex) {
    return (int) evidenceSource.findCandidateEvidenceIds(sessionId, turnIndex)
        .stream()
        .filter(executionId -> evidenceStore.attach(sessionId, turnIndex, executionId))
        .count();
  }
}
