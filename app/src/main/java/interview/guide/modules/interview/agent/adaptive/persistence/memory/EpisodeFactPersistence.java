package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeClosureStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactCreation;
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

  public EpisodeFactEntity create(EpisodePersistenceInput input) {
    EpisodeFactCreation creation = new EpisodeFactCreation(
        new MemoryOwner(input.session().tenantId(), input.session().candidateId()),
        input.session().id(),
        input.session().toDomain().settings().mode(),
        input.turn().id(),
        input.assessment().turnIndex(),
        input.dimension().topic(),
        input.before().activeTargetId(),
        input.before().revision(),
        input.after().revision(),
        assistance(input),
        closure(input),
        input.correctsEpisodeId()
    );
    EpisodeFactEntity episode = repository.save(
        new EpisodeFactEntity(creation, input.assessment()));
    semanticMemory.record(new SemanticContributionInput(
        episode.toDomain(),
        input.assessment().depthLevel(),
        input.dimension().expectedDepth()
    ));
    eventPublisher.publishEvent(new EpisodeEnrichmentRequested(
        episode.id(),
        input.session().llmProvider()
    ));
    return episode;
  }

  private EpisodeAssistanceLevel assistance(EpisodePersistenceInput input) {
    TurnTriggerType trigger = input.turn().toDomain().provenance().trigger().type();
    return switch (trigger) {
      case PLANNED -> EpisodeAssistanceLevel.NONE;
      case ASSESSMENT_GAP -> EpisodeAssistanceLevel.FOLLOW_UP;
      case TOOL_RESULT -> EpisodeAssistanceLevel.TOOL_ASSISTED;
    };
  }

  private EpisodeClosureStatus closure(EpisodePersistenceInput input) {
    TargetWorkStatus status = input.after().targets().stream()
        .filter(target -> target.targetId().equals(input.before().activeTargetId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Episode 目标不存在"))
        .status();
    return switch (status) {
      case COMPLETED -> EpisodeClosureStatus.RESOLVED;
      case EXHAUSTED -> EpisodeClosureStatus.ABANDONED;
      case PENDING, ACTIVE -> EpisodeClosureStatus.UNRESOLVED;
    };
  }
}
