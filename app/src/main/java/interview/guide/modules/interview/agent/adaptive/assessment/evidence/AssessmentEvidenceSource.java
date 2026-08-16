package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import java.util.Set;

/**
 * 评估证据来源接口。
 */
public interface AssessmentEvidenceSource {

  AssessmentEvidenceFacts load(
      String sessionId,
      int turnIndex,
      Set<String> toolResultIds
  );
}
