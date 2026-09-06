package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMastery;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeOutcome;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticePlanningMemory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeResult;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeSemanticState;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeStatistics;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.LatestPractice;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferAssessment;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.TransferStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryPracticeScenarioTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey REDIS_PERSISTENCE = new TopicKey("redis", "persistence");
  private static final TopicKey REDIS_CLUSTER = new TopicKey("redis", "cluster");

  @Test
  @DisplayName("练习规划限定 scope，提示后作答只更新为辅助掌握")
  void shouldPlanWithinScopeAndRecordAssistedMastery() {
    PracticeMemoryService memory = memoryService();
    PracticePlanningMemory planning = memory.planning(
        OWNER, new PracticeScope(List.of(REDIS_PERSISTENCE)));
    PracticeContribution retest = new PracticeContribution(
        new SemanticSource(12L, OWNER, REDIS_PERSISTENCE,
            LocalDateTime.of(2026, 8, 28, 12, 0)),
        new PracticeResult(
            PracticeOutcome.COMPLETED, EpisodeAssistanceLevel.HINT, DepthLevel.L2)
    );
    PracticeAggregate updated = new SemanticAggregator()
        .practice(List.of(retest), List.of(), List.of());

    assertThat(planning.topics()).extracting(topic -> topic.topic())
        .containsExactly(REDIS_PERSISTENCE);
    assertThat(updated.mastery()).isEqualTo(PracticeMastery.ASSISTED);
    assertThat(updated.statistics().completed(EpisodeAssistanceLevel.HINT)).isEqualTo(1);
    assertThat(updated.transfer().status()).isEqualTo(TransferStatus.NOT_REEVALUATED);
  }

  private PracticeMemoryService memoryService() {
    PracticeSemanticState selected = state(REDIS_PERSISTENCE, PracticeMastery.UNRESOLVED);
    PracticeSemanticState outsideScope = state(REDIS_CLUSTER, PracticeMastery.INDEPENDENT);
    return new PracticeMemoryService(owner -> List.of(selected, outsideScope));
  }

  private PracticeSemanticState state(TopicKey topic, PracticeMastery mastery) {
    LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
    PracticeResult result = new PracticeResult(
        mastery == PracticeMastery.UNRESOLVED
            ? PracticeOutcome.UNRESOLVED
            : PracticeOutcome.COMPLETED,
        EpisodeAssistanceLevel.NONE,
        DepthLevel.L2
    );
    return new PracticeSemanticState(
        new SemanticStateKey(OWNER, topic, SemanticTrack.PRACTICE_MASTERY),
        1L,
        new PracticeStatistics(Map.of(), mastery == PracticeMastery.UNRESOLVED ? 1 : 0,
            new LatestPractice(10L, result, time)),
        mastery,
        List.of(),
        new TransferAssessment(TransferStatus.NOT_REEVALUATED, null),
        time
    );
  }

}
