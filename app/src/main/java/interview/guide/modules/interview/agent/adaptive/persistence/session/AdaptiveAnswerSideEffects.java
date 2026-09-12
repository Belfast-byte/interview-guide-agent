package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactPersistence.AgentEpisodePersistenceInput.AssessmentTarget;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactPersistence.AgentEpisodePersistenceInput;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposure.QuestionPublication;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposure.QuestionIdentity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import org.springframework.stereotype.Component;

/** 最终事实提交时派生 Episode 与问题曝光。 */
@Component
class AdaptiveAnswerSideEffects {

  private final EpisodeFactPersistence episodes;
  private final QuestionExposurePersistence exposures;

  AdaptiveAnswerSideEffects(
      EpisodeFactPersistence episodes,
      QuestionExposurePersistence exposures
  ) {
    this.episodes = episodes;
    this.exposures = exposures;
  }

  void saveEpisode(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentTurnEntity turn,
      EpisodeAssessment assessment
  ) {
    episodes.create(new AgentEpisodePersistenceInput(
        session,
        turn,
        new AssessmentTarget(
            assessment.assessment(), assessment.dimension(), assessment.targetId())
    ));
  }

  void saveExposure(QuestionExposureInput input) {
    RespondAction action = input.question().action();
    exposures.save(input.session(), input.turn(), new QuestionPublication(
        action,
        QuestionIdentity.from(input.question().target().target()),
        null,
        null
    ));
  }

  record EpisodeAssessment(
      AdaptiveAgentAssessmentEntity assessment,
      PlannedDimension dimension,
      String targetId
  ) {}

  record QuestionExposureInput(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentTurnEntity turn,
      QuestionTarget question
  ) {}

  record QuestionTarget(PlannedDimension target, RespondAction action) {}
}
