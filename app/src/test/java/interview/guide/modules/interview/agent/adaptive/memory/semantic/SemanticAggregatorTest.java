package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.episode.AnswerHabit;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeTagValue;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemanticAggregatorTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey TOPIC = new TopicKey("redis", "persistence");
  private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 28, 9, 0);
  private final SemanticAggregator aggregator = new SemanticAggregator();

  @Test
  @DisplayName("正式能力按 L0-L4 加权公式聚合")
  void shouldAggregateEvaluatedAbility() {
    EvaluationAggregate aggregate = aggregator.evaluation(List.of(
        evaluation(1, DepthLevel.L1, 1),
        evaluation(2, DepthLevel.L3, 2)
    ), List.of());

    assertThat(aggregate.ability()).isEqualTo(EvaluatedAbility.COMPETENT);
    assertThat(aggregate.statistics().count(DepthLevel.L1)).isEqualTo(1);
    assertThat(aggregate.statistics().count(DepthLevel.L3)).isEqualTo(1);
  }

  @Test
  @DisplayName("练习掌握度只取最新一次有效练习并累计辅助次数")
  void shouldUseLatestPracticeResult() {
    PracticeAggregate aggregate = aggregator.practice(List.of(
        practice(1, completed(EpisodeAssistanceLevel.FOLLOW_UP), 1),
        practice(2, completed(EpisodeAssistanceLevel.NONE), 2)
    ), List.of(), List.of());

    assertThat(aggregate.mastery()).isEqualTo(PracticeMastery.INDEPENDENT);
    assertThat(aggregate.statistics().completed(EpisodeAssistanceLevel.FOLLOW_UP)).isEqualTo(1);
    assertThat(aggregate.statistics().completed(EpisodeAssistanceLevel.NONE)).isEqualTo(1);
  }

  @Test
  @DisplayName("稳定模式必须来自两个不同 Episode")
  void shouldRequireTwoEpisodesForStablePattern() {
    EpisodeTagValue habit = EpisodeTagValue.habit(
        AnswerHabit.IMPLEMENTATION_WITHOUT_TRADEOFF);
    List<SemanticPatternSource> patterns = List.of(
        new SemanticPatternSource(1, habit),
        new SemanticPatternSource(1, habit),
        new SemanticPatternSource(2, habit)
    );

    EvaluationAggregate aggregate = aggregator.evaluation(List.of(
        evaluation(1, DepthLevel.L1, 1),
        evaluation(2, DepthLevel.L1, 2)
    ), patterns);

    assertThat(aggregate.stablePatterns())
        .containsExactly(new StablePattern(habit, 2));
  }

  @Test
  @DisplayName("只有练习之后的正式 Episode 能确认或回退能力迁移")
  void shouldTransferOnlyFromLaterEvaluation() {
    PracticeContribution practice = practice(
        2, completed(EpisodeAssistanceLevel.NONE), 2);

    PracticeAggregate confirmed = aggregator.practice(
        List.of(practice),
        List.of(
            evaluation(1, DepthLevel.L4, 1),
            evaluation(3, DepthLevel.L2, 3)
        ),
        List.of()
    );

    assertThat(confirmed.transfer())
        .isEqualTo(new TransferAssessment(TransferStatus.CONFIRMED, 3L));
    assertThat(confirmed.mastery()).isEqualTo(PracticeMastery.INDEPENDENT);
  }

  private EvaluationContribution evaluation(long episodeId, DepthLevel level, int minute) {
    return new EvaluationContribution(source(episodeId, minute), level);
  }

  private PracticeContribution practice(
      long episodeId,
      PracticeResult result,
      int minute
  ) {
    return new PracticeContribution(source(episodeId, minute), result);
  }

  private PracticeResult completed(EpisodeAssistanceLevel assistance) {
    return new PracticeResult(PracticeOutcome.COMPLETED, assistance, DepthLevel.L2);
  }

  private SemanticSource source(long episodeId, int minute) {
    return new SemanticSource(episodeId, OWNER, TOPIC, BASE.plusMinutes(minute));
  }
}
