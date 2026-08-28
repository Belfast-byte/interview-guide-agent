package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EpisodeFactTest {

  @Test
  @DisplayName("Episode WorkState revision 必须严格前进")
  void shouldRequireIncreasingWorkRevision() {
    assertThatThrownBy(() -> creation(2, 2, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("revision");
  }

  @Test
  @DisplayName("纠正 Episode 必须引用有效历史 Episode")
  void shouldRequireValidCorrectionReference() {
    assertThatThrownBy(() -> creation(1, 2, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("纠正");
  }

  private EpisodeFactCreation creation(
      long before,
      long after,
      Long correctsEpisodeId
  ) {
    return new EpisodeFactCreation(
        new MemoryOwner(null, "candidate-1"),
        "session-1",
        SessionMode.PRACTICE,
        10,
        2,
        new TopicKey("java-backend", "REDIS"),
        "target-0",
        before,
        after,
        EpisodeAssistanceLevel.FOLLOW_UP,
        EpisodeClosureStatus.RESOLVED,
        correctsEpisodeId
    );
  }
}
