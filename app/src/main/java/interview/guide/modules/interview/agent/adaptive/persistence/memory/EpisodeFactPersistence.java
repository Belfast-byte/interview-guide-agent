package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.profile.AbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 在回答短事务中创建 EpisodeFact。
 */
@Component
@RequiredArgsConstructor
public class EpisodeFactPersistence {

  private final EpisodeFactRepository repository;
  private final AbilityCounterIncrementStore counterIncrementStore;
  private final ApplicationEventPublisher eventPublisher;

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
    EpisodeFactEntity episode = repository.save(new EpisodeFactEntity(creation, assessment));
    counterIncrementStore.increment(
        creation.owner(), creation.topic(), assessment.depthLevel()
    );
    eventPublisher.publishEvent(new EpisodeEnrichmentRequested(
        episode.id(),
        session.llmProvider()
    ));
    return episode;
  }
}
