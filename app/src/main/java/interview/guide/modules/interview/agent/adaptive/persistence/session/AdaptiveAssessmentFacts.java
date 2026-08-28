package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import java.util.List;

public record AdaptiveAssessmentFacts(
    AssessmentDecision decision,
    List<ValidatedAssessmentEvidence> evidences,
    List<PracticeRecommendation> recommendations
) {

  public AdaptiveAssessmentFacts {
    evidences = List.copyOf(evidences);
    recommendations = List.copyOf(recommendations);
  }
}
