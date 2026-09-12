package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactEntity.Creation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 与评估一起提交，包括直接 FINISH 的最后一题；无需事后记忆任务。 */
@Component
@RequiredArgsConstructor
public class EpisodeFactPersistence {
  private final EpisodeFactRepository repository;

  public EpisodeFactEntity create(AgentEpisodePersistenceInput input) {
    var target = input.assessmentTarget();
    return repository.save(new EpisodeFactEntity(new Creation(
        new MemoryOwner(input.session().tenantId(), input.session().candidateId()),
        input.session().id(), input.session().toDomain().settings().mode(),
        input.turn().id(), target.assessment().turnIndex(), target.dimension().topic(),
        target.targetId()), target.assessment()));
  }

  public record AgentEpisodePersistenceInput(
      AdaptiveAgentSessionEntity session, AdaptiveAgentTurnEntity turn,
      AssessmentTarget assessmentTarget
  ) {
    public record AssessmentTarget(AdaptiveAgentAssessmentEntity assessment,
        PlannedDimension dimension, String targetId) {}
  }
}
