package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AgentEpisodeFactCreation;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionInput;
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
  private final SemanticMemoryPersistenceService semanticMemory;
  private final ApplicationEventPublisher eventPublisher;

  public EpisodeFactEntity create(AgentEpisodePersistenceInput input) {
    var assessmentTarget = input.assessmentTarget();
    var ownership = new AgentEpisodeFactCreation.Ownership(
        new MemoryOwner(input.session().tenantId(), input.session().candidateId()),
        input.session().id(),
        input.session().toDomain().settings().mode()
    );
    var source = new AgentEpisodeFactCreation.Source(
        input.turn().id(),
        assessmentTarget.assessment().turnIndex(),
        assessmentTarget.dimension().topic()
    );
    var evaluation = new AgentEpisodeFactCreation.Evaluation(
        assessmentTarget.targetId(),
        assistance(input.turn().toDomain().provenance().trigger().type()),
        EpisodeClosureStatus.UNRESOLVED
    );
    EpisodeFactEntity episode = repository.save(new EpisodeFactEntity(
        new AgentEpisodeFactCreation(ownership, source, evaluation),
        assessmentTarget.assessment()
    ));
    semanticMemory.record(new SemanticContributionInput(
        episode.toDomain(),
        assessmentTarget.assessment().depthLevel(),
        assessmentTarget.dimension().expectedDepth()
    ));
    eventPublisher.publishEvent(new EpisodeEnrichmentRequested(
        episode.id(), input.session().llmProvider()));
    return episode;
  }

  private EpisodeAssistanceLevel assistance(TurnTriggerType trigger) {
    return switch (trigger) {
      case PLANNED, AGENT_DECISION -> EpisodeAssistanceLevel.NONE;
      case ASSESSMENT_GAP -> EpisodeAssistanceLevel.FOLLOW_UP;
    };
  }

}
