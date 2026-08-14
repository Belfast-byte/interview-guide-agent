package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

public interface AssessmentBackfillStore {

  List<AssessmentBackfillTurn> findMissing(String sessionId);

  void save(
      AssessmentBackfillTurn turn,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  );
}
