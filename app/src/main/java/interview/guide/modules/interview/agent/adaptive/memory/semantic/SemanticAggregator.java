package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SemanticAggregator {

  public static final int MIN_PATTERN_EPISODES = 2;

  public EvaluationAggregate evaluation(
      List<EvaluationContribution> contributions,
      List<SemanticPatternSource> patterns
  ) {
    long[] counts = new long[DepthLevel.values().length];
    contributions.forEach(contribution -> counts[contribution.level().ordinal()]++);
    EvaluationStatistics statistics = new EvaluationStatistics(
        java.util.Arrays.stream(counts).boxed().toList());
    return new EvaluationAggregate(
        statistics,
        statistics.ability(),
        stablePatterns(episodeIds(contributions), patterns)
    );
  }

  public PracticeAggregate practice(
      List<PracticeContribution> practices,
      List<EvaluationContribution> evaluations,
      List<SemanticPatternSource> patterns
  ) {
    LatestPractice latest = latest(practices);
    PracticeStatistics statistics = practiceStatistics(practices, latest);
    return new PracticeAggregate(
        statistics,
        latest.mastery(),
        stablePatterns(episodeIds(practices), patterns),
        transfer(latest, evaluations)
    );
  }

  private PracticeStatistics practiceStatistics(
      List<PracticeContribution> practices,
      LatestPractice latest
  ) {
    Map<EpisodeAssistanceLevel, Long> completed = new EnumMap<>(EpisodeAssistanceLevel.class);
    long unresolved = 0;
    for (PracticeContribution practice : practices) {
      if (practice.result().outcome() == PracticeOutcome.UNRESOLVED) {
        unresolved++;
      } else {
        completed.merge(practice.result().assistance(), 1L, Long::sum);
      }
    }
    return new PracticeStatistics(completed, unresolved, latest);
  }

  private LatestPractice latest(List<PracticeContribution> practices) {
    PracticeContribution latest = practices.stream()
        .max(Comparator.comparing((PracticeContribution value) -> value.source().createdAt())
            .thenComparing(value -> value.source().episodeId()))
        .orElseThrow(() -> new IllegalArgumentException("练习聚合缺少 contribution"));
    return new LatestPractice(
        latest.source().episodeId(), latest.result(), latest.source().createdAt());
  }

  private TransferAssessment transfer(
      LatestPractice practice,
      List<EvaluationContribution> evaluations
  ) {
    return evaluations.stream()
        .filter(value -> value.source().createdAt().isAfter(practice.createdAt()))
        .max(Comparator.comparing((EvaluationContribution value) -> value.source().createdAt())
            .thenComparing(value -> value.source().episodeId()))
        .map(value -> transfer(practice, value))
        .orElse(new TransferAssessment(TransferStatus.NOT_REEVALUATED, null));
  }

  private TransferAssessment transfer(
      LatestPractice practice,
      EvaluationContribution evaluation
  ) {
    TransferStatus status = evaluation.level().ordinal()
            >= practice.result().targetDepth().ordinal()
        ? TransferStatus.CONFIRMED
        : TransferStatus.REGRESSED;
    return new TransferAssessment(status, evaluation.source().episodeId());
  }

  private List<StablePattern> stablePatterns(
      Set<Long> episodeIds,
      List<SemanticPatternSource> patterns
  ) {
    Map<EpisodeTagValue, Set<Long>> sources = new HashMap<>();
    patterns.stream()
        .filter(pattern -> episodeIds.contains(pattern.episodeId()))
        .forEach(pattern -> sources.computeIfAbsent(
            pattern.value(), ignored -> new HashSet<>()).add(pattern.episodeId()));
    List<StablePattern> stable = new ArrayList<>();
    sources.forEach((value, ids) -> {
      if (ids.size() >= MIN_PATTERN_EPISODES) {
        stable.add(new StablePattern(value, ids.size()));
      }
    });
    return stable.stream()
        .sorted(Comparator.comparing((StablePattern value) -> value.value().category())
            .thenComparing(value -> value.value().tag()))
        .toList();
  }

  private Set<Long> episodeIds(List<? extends SemanticContribution> contributions) {
    return contributions.stream()
        .map(contribution -> contribution.source().episodeId())
        .collect(java.util.stream.Collectors.toSet());
  }
}
