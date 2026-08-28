package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;

public record EpisodePersistenceInput(
    AdaptiveAgentSessionEntity session,
    AdaptiveAgentTurnEntity turn,
    AdaptiveAgentAssessmentEntity assessment,
    PlannedDimension dimension,
    InterviewWorkState before,
    InterviewWorkState after,
    Long correctsEpisodeId
) {}
