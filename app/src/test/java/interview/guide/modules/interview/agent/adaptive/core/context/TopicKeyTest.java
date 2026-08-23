package interview.guide.modules.interview.agent.adaptive.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopicKeyTest {

  @Test
  @DisplayName("主题身份由 skillId 与 focusId 共同决定")
  void shouldUseSkillAndFocusAsIdentity() {
    TopicKey redis = new TopicKey("java-backend", "REDIS");

    assertThat(redis).isEqualTo(new TopicKey("java-backend", "REDIS"));
    assertThat(redis).isNotEqualTo(new TopicKey("system-design", "REDIS"));
  }

  @Test
  @DisplayName("主题标识在系统边界去除首尾空白")
  void shouldNormalizeWhitespace() {
    assertThat(new TopicKey(" java-backend ", " REDIS "))
        .isEqualTo(new TopicKey("java-backend", "REDIS"));
  }

  @Test
  @DisplayName("空主题标识被明确拒绝")
  void shouldRejectBlankIdentity() {
    assertThatThrownBy(() -> new TopicKey(" ", "REDIS"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TopicKey("java-backend", null))
        .isInstanceOf(NullPointerException.class);
  }
}
