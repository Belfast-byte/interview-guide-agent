package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionFactory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticContributionInput;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticOwnerTopic;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticPatternSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SemanticMemoryPersistenceService {

  private final SemanticMemoryRepositories repositories;
  private final SemanticContributionFactory contributionFactory;
  private final SemanticAggregator aggregator;

  @Transactional
  public SemanticContribution record(SemanticContributionInput input) {
    SemanticContribution contribution = contributionFactory.create(input);
    var existing = repositories.contributions()
        .findByEpisodeIdAndTrack(
            contribution.source().episodeId(), contribution.track());
    if (existing.isPresent()) {
      return existing.orElseThrow().toDomain();
    }
    SemanticContribution stored = repositories.contributions()
        .saveAndFlush(new SemanticContributionEntity(contribution))
        .toDomain();
    recompute(new SemanticOwnerTopic(stored.source().owner(), stored.source().topic()));
    return stored;
  }

  @Transactional
  public void refreshForEpisode(long episodeId) {
    SemanticContribution contribution = repositories.contributions()
        .findByEpisodeId(episodeId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "Episode 对应的 Semantic contribution 不存在"
        ))
        .toDomain();
    recompute(new SemanticOwnerTopic(
        contribution.source().owner(), contribution.source().topic()));
  }

  private void recompute(SemanticOwnerTopic scope) {
    List<SemanticContribution> contributions = repositories.contributions()
        .findByOwnerAndTopic(scope)
        .stream()
        .map(SemanticContributionEntity::toDomain)
        .toList();
    List<EvaluationContribution> evaluations = contributions.stream()
        .filter(EvaluationContribution.class::isInstance)
        .map(EvaluationContribution.class::cast)
        .toList();
    List<PracticeContribution> practices = contributions.stream()
        .filter(PracticeContribution.class::isInstance)
        .map(PracticeContribution.class::cast)
        .toList();
    List<SemanticPatternSource> patterns = patterns(contributions);
    SemanticAggregationFacts facts = new SemanticAggregationFacts(
        scope, new ContributionsByTrack(evaluations, practices), patterns);
    storeEvaluation(facts);
    storePractice(facts);
  }

  private List<SemanticPatternSource> patterns(
      List<SemanticContribution> contributions
  ) {
    List<Long> episodeIds = contributions.stream()
        .map(value -> value.source().episodeId())
        .toList();
    return repositories.tags().findByEpisodeIdIn(episodeIds).stream()
        .map(EpisodeTagEntity::toDomain)
        .map(tag -> new SemanticPatternSource(tag.episodeId(), tag.value()))
        .toList();
  }

  private void storeEvaluation(SemanticAggregationFacts facts) {
    if (facts.contributions().evaluations().isEmpty()) {
      return;
    }
    EvaluationAggregate aggregate = aggregator.evaluation(
        facts.contributions().evaluations(), facts.patterns());
    SemanticStateKey key = new SemanticStateKey(
        facts.scope().owner(), facts.scope().topic(), SemanticTrack.EVALUATED_CAPABILITY);
    SemanticStateEntity state = repositories.states().findLocked(key)
        .orElseGet(() -> new SemanticStateEntity(key));
    state.apply(aggregate);
    repositories.states().saveAndFlush(state);
  }

  private void storePractice(SemanticAggregationFacts facts) {
    if (facts.contributions().practices().isEmpty()) {
      return;
    }
    PracticeAggregate aggregate = aggregator.practice(
        facts.contributions().practices(),
        facts.contributions().evaluations(),
        facts.patterns());
    SemanticStateKey key = new SemanticStateKey(
        facts.scope().owner(), facts.scope().topic(), SemanticTrack.PRACTICE_MASTERY);
    SemanticStateEntity state = repositories.states().findLocked(key)
        .orElseGet(() -> new SemanticStateEntity(key));
    state.apply(aggregate);
    repositories.states().saveAndFlush(state);
  }

  private record SemanticAggregationFacts(
      SemanticOwnerTopic scope,
      ContributionsByTrack contributions,
      List<SemanticPatternSource> patterns
  ) {}

  private record ContributionsByTrack(
      List<EvaluationContribution> evaluations,
      List<PracticeContribution> practices
  ) {}
}
