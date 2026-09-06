package interview.guide.modules.interview.agent.adaptive.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluatedAbility;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationAggregate;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.EvaluationContribution;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAggregator;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticSource;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryEvaluationScenarioTest {

  private static final TopicKey REDIS_PERSISTENCE =
      new TopicKey("redis", "persistence");

  @Test
  @DisplayName("正式贡献保留当前回答来源并参与能力聚合")
  void shouldAggregateCurrentEvaluationContribution() {
    EvaluationContribution current = new EvaluationContribution(
        new SemanticSource(202L, new MemoryOwner(null, "candidate-1"),
            REDIS_PERSISTENCE, LocalDateTime.of(2026, 8, 28, 11, 0)),
        DepthLevel.L2
    );
    EvaluationAggregate aggregate = new SemanticAggregator()
        .evaluation(List.of(current), List.of());

    assertThat(current.source().episodeId()).isEqualTo(202L);
    assertThat(aggregate.ability()).isEqualTo(EvaluatedAbility.COMPETENT);
  }

}
