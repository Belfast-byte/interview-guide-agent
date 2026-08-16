package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.List;

/**
 * 评估回填存储接口。
 */
public interface AssessmentBackfillStore {

  List<AssessmentBackfillTurn> findMissing(String sessionId);

  void save(
      AssessmentBackfillTurn turn,
      AssessmentDecision assessment,
      List<ValidatedAssessmentEvidence> evidences
  );
}
