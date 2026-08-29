package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;

public record AgentEpisodePersistenceInput(
    AdaptiveAgentSessionEntity session,
    AdaptiveAgentTurnEntity turn,
    AssessmentTarget assessmentTarget
) {

  public record AssessmentTarget(
      AdaptiveAgentAssessmentEntity assessment,
      PlannedDimension dimension,
      String targetId
  ) {}
}
