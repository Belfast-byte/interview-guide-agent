package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 从不可变 contribution 与 enrichment tag 按读投影当前语义画像。 */
@Component
public final class SemanticStateProjector {

  private final SemanticAggregator aggregator;

  public SemanticStateProjector(SemanticAggregator aggregator) {
    this.aggregator = aggregator;
  }

  public List<SemanticState> project(
      List<SemanticContribution> contributions,
      List<SemanticPatternSource> patterns
  ) {
    Map<SemanticOwnerTopic, List<SemanticContribution>> grouped = new LinkedHashMap<>();
    contributions.forEach(contribution -> grouped.computeIfAbsent(
        new SemanticOwnerTopic(
            contribution.source().owner(), contribution.source().topic()),
        ignored -> new ArrayList<>()
    ).add(contribution));
    return grouped.entrySet().stream()
        .flatMap(entry -> project(entry.getKey(), entry.getValue(), patterns).stream())
        .toList();
  }

  private List<SemanticState> project(
      SemanticOwnerTopic scope,
      List<SemanticContribution> contributions,
      List<SemanticPatternSource> patterns
  ) {
    List<EvaluationContribution> evaluations = contributions.stream()
        .filter(EvaluationContribution.class::isInstance)
        .map(EvaluationContribution.class::cast)
        .toList();
    List<PracticeContribution> practices = contributions.stream()
        .filter(PracticeContribution.class::isInstance)
        .map(PracticeContribution.class::cast)
        .toList();
    List<SemanticState> states = new ArrayList<>();
    if (!evaluations.isEmpty()) {
      states.add(evaluation(scope, evaluations, patterns));
    }
    if (!practices.isEmpty()) {
      states.add(practice(scope, evaluations, practices, patterns));
    }
    return states;
  }

  private EvaluationSemanticState evaluation(
      SemanticOwnerTopic scope,
      List<EvaluationContribution> evaluations,
      List<SemanticPatternSource> patterns
  ) {
    EvaluationAggregate aggregate = aggregator.evaluation(evaluations, patterns);
    return new EvaluationSemanticState(
        key(scope, SemanticTrack.EVALUATED_CAPABILITY),
        evaluations.size(),
        aggregate.statistics(),
        aggregate.ability(),
        aggregate.stablePatterns(),
        latest(evaluations)
    );
  }

  private PracticeSemanticState practice(
      SemanticOwnerTopic scope,
      List<EvaluationContribution> evaluations,
      List<PracticeContribution> practices,
      List<SemanticPatternSource> patterns
  ) {
    PracticeAggregate aggregate = aggregator.practice(practices, evaluations, patterns);
    return new PracticeSemanticState(
        key(scope, SemanticTrack.PRACTICE_MASTERY),
        practices.size() + evaluations.size(),
        aggregate.statistics(),
        aggregate.mastery(),
        aggregate.stablePatterns(),
        aggregate.transfer(),
        latest(contributions(evaluations, practices))
    );
  }

  private SemanticStateKey key(SemanticOwnerTopic scope, SemanticTrack track) {
    return new SemanticStateKey(scope.owner(), scope.topic(), track);
  }

  private List<SemanticContribution> contributions(
      List<EvaluationContribution> evaluations,
      List<PracticeContribution> practices
  ) {
    List<SemanticContribution> result = new ArrayList<>(evaluations);
    result.addAll(practices);
    return result;
  }

  private LocalDateTime latest(List<? extends SemanticContribution> contributions) {
    return contributions.stream()
        .map(contribution -> contribution.source().createdAt())
        .max(LocalDateTime::compareTo)
        .orElseThrow();
  }
}
