package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PracticeMemoryServiceTest {

  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-1");
  private static final TopicKey REDIS = new TopicKey("redis", "persistence");
  private static final TopicKey JVM = new TopicKey("java", "jvm");

  @Test
  @DisplayName("练习 planning view 保持请求 scope 且不会被画像扩张")
  void shouldKeepPlanningViewInsideScope() {
    SemanticStateSource source = mock(SemanticStateSource.class);
    when(source.findByOwner(OWNER)).thenReturn(List.of(evaluation(REDIS), evaluation(JVM)));

    PracticePlanningMemory memory = new PracticeMemoryService(source)
        .planning(OWNER, new PracticeScope(List.of(REDIS)));

    assertThat(memory.topics())
        .extracting(PracticePlanningTopic::topic)
        .containsExactly(REDIS);
    assertThat(memory.topics().getFirst().status().evaluatedAbility())
        .isEqualTo(EvaluatedAbility.COMPETENT);
  }

  private EvaluationSemanticState evaluation(TopicKey topic) {
    EvaluationStatistics statistics = new EvaluationStatistics(List.of(0L, 0L, 1L, 0L, 0L));
    return new EvaluationSemanticState(
        new SemanticStateKey(OWNER, topic, SemanticTrack.EVALUATED_CAPABILITY),
        1,
        statistics,
        statistics.ability(),
        List.of(),
        LocalDateTime.of(2026, 8, 28, 10, 0)
    );
  }
}
