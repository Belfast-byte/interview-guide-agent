package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.Set;

public interface AssessmentEvidenceSource {

  AssessmentEvidenceFacts load(
      String sessionId,
      int turnIndex,
      Set<String> toolResultIds
  );
}
