package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbilityCounterTest {

  @Test
  @DisplayName("零样本不生成能力等级")
  void shouldNotRateEmptyCounter() {
    assertThat(AbilityCounter.empty().ability()).isEmpty();
  }

  @Test
  @DisplayName("加权均值达到 3 时为 PROFICIENT")
  void shouldRateProficientAtBoundary() {
    AbilityCounter counter = new AbilityCounter(0, 0, 1, 0, 1);

    assertThat(counter.weightedTotal()).isEqualTo(6);
    assertThat(counter.ability()).contains(SemanticAbility.PROFICIENT);
    assertThat(new AbilityCounter(0, 0, 0, 1, 0).ability())
        .contains(SemanticAbility.PROFICIENT);
  }

  @Test
  @DisplayName("加权均值达到 2 时为 COMPETENT 否则为 WEAK")
  void shouldRateCompetentAndWeakAtBoundary() {
    assertThat(new AbilityCounter(0, 0, 1, 0, 0).ability())
        .contains(SemanticAbility.COMPETENT);
    assertThat(new AbilityCounter(0, 1, 0, 0, 0).ability())
        .contains(SemanticAbility.WEAK);
  }

  @Test
  @DisplayName("增减只改变目标 DepthLevel 的计数")
  void shouldIncrementAndDecrementSelectedLevel() {
    AbilityCounter counter = AbilityCounter.empty()
        .increment(DepthLevel.L4)
        .increment(DepthLevel.L2)
        .decrement(DepthLevel.L4);

    assertThat(counter).isEqualTo(new AbilityCounter(0, 0, 1, 0, 0));
  }

  @Test
  @DisplayName("构造负数或从零递减时明确失败")
  void shouldRejectNegativeCounters() {
    assertThatThrownBy(() -> new AbilityCounter(-1, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AbilityCounter.empty().decrement(DepthLevel.L0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("下溢");
  }
}
