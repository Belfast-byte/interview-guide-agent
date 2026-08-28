package interview.guide.modules.interview.agent.adaptive.memory.episode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionNoveltyPolicyTest {

  private final QuestionNoveltyPolicy policy = new QuestionNoveltyPolicy();

  @Test
  @DisplayName("相似度不足且场景不同的草稿直接发布")
  void shouldAcceptNovelQuestion() {
    var decision = policy.decide(identity("新场景", "新措辞"), List.of(
        recall("旧场景", "旧问题", 0.42)));

    assertThat(decision.type()).isEqualTo(QuestionNoveltyDecision.Type.ACCEPT);
  }

  @Test
  @DisplayName("只换措辞但语义仍重复时要求换场景")
  void shouldRewriteSemanticDuplicate() {
    var decision = policy.decide(identity("新场景", "新措辞"), List.of(
        recall("旧场景", "换过措辞的问题", 0.91)));

    assertThat(decision.type()).isEqualTo(QuestionNoveltyDecision.Type.REWRITE);
    assertThat(decision.sourceExposureId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("换场景不得改变 Topic 深度难度或证据目标")
  void shouldRejectChangedTargetEnvelope() {
    QuestionIdentity original = identity("old", "old");
    QuestionIdentity changed = new QuestionIdentity(
        original.topic(), "另一个目标", original.probeDepth(), original.difficulty(),
        "new", "new");

    assertThatThrownBy(() -> policy.requireSameEnvelope(original, changed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("换场景问题改变了 TargetEnvelope");
  }

  private QuestionIdentity identity(String scenario, String wording) {
    return new QuestionIdentity(
        new TopicKey("java-backend", "REDIS_PERSISTENCE"),
        "解释数据丢失边界",
        DepthLevel.L2,
        "L2",
        scenario,
        wording
    );
  }

  private EvaluationRecallView recall(
      String scenario,
      String question,
      double similarity
  ) {
    return new EvaluationRecallView(
        7,
        9L,
        question,
        scenario,
        new TopicKey("java-backend", "REDIS_PERSISTENCE"),
        "解释数据丢失边界",
        DepthLevel.L2,
        "L2",
        similarity,
        "重新验证故障窗口"
    );
  }
}
