package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 在回答短事务中创建 EpisodeFact。
 */
@Component
@RequiredArgsConstructor
public class EpisodeFactPersistence {

  private final EpisodeFactRepository repository;

  public EpisodeFactEntity create(
      AdaptiveAgentSessionEntity session,
      AdaptiveAgentAssessmentEntity assessment,
      PlannedDimension dimension
  ) {
    EpisodeFactCreation creation = new EpisodeFactCreation(
        new MemoryOwner(session.tenantId(), session.candidateId()),
        session.id(),
        assessment.turnIndex(),
        new TopicKey(dimension.suggestedSkill(), dimension.focusId())
    );
    return repository.save(new EpisodeFactEntity(creation, assessment));
  }
}
