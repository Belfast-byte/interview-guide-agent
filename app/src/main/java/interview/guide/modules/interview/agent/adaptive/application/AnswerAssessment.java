package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;

/** 回答推进进入 Agent Loop 前的正式事实提案。 */
public record AnswerAssessment(
    PlannedDimension dimension,
    AssessmentDecision decision,
    List<ValidatedAssessmentEvidence> evidences
) {

  public AnswerAssessment {
    evidences = List.copyOf(evidences);
  }
}
