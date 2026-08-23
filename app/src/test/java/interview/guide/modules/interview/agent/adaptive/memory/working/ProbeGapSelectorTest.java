package interview.guide.modules.interview.agent.adaptive.memory.working;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProbeGapSelectorTest {

  private static final TopicKey REDIS = new TopicKey("java-backend", "REDIS");

  @Test
  @DisplayName("乱序输入按 gapOrder 再按 id 选择第一条")
  void shouldSelectByStableOrder() {
    ProbeGapCandidate second = candidate(20, 1, 2);
    ProbeGapCandidate firstWithLargerId = candidate(12, 1, 1);
    ProbeGapCandidate first = candidate(11, 1, 1);

    assertThat(ProbeGapSelector.select(
        REDIS,
        List.of(second, firstWithLargerId, first),
        Set.of()
    )).contains(first);
  }

  @Test
  @DisplayName("已被 turn 引用的 Assessment gaps 不再选择")
  void shouldExcludeUsedAssessment() {
    ProbeGapCandidate used = candidate(11, 7, 1);
    ProbeGapCandidate available = candidate(12, 8, 2);

    assertThat(ProbeGapSelector.select(
        REDIS,
        List.of(used, available),
        Set.of(7L)
    )).contains(available);
  }

  @Test
  @DisplayName("只选择当前 TopicKey 的 gap")
  void shouldExcludeOtherTopics() {
    ProbeGapCandidate otherSkill = candidate(
        10,
        7,
        new TopicKey("system-design", "REDIS")
    );
    ProbeGapCandidate current = candidate(20, 8, 2);

    assertThat(ProbeGapSelector.select(
        REDIS,
        List.of(otherSkill, current),
        Set.of()
    )).contains(current);
  }

  @Test
  @DisplayName("没有当前主题的未使用 gap 时返回空")
  void shouldReturnEmptyWithoutAvailableGap() {
    assertThat(ProbeGapSelector.select(
        REDIS,
        List.of(candidate(11, 7, 1)),
        Set.of(7L)
    )).isEmpty();
  }

  private ProbeGapCandidate candidate(
      long id,
      long assessmentId,
      int order
  ) {
    return new ProbeGapCandidate(
        id,
        assessmentId,
        REDIS,
        order,
        new ProbeGap("锚点-" + id, "缺口-" + id)
    );
  }

  private ProbeGapCandidate candidate(
      long id,
      long assessmentId,
      TopicKey topic
  ) {
    return new ProbeGapCandidate(
        id,
        assessmentId,
        topic,
        1,
        new ProbeGap("锚点-" + id, "缺口-" + id)
    );
  }
}
