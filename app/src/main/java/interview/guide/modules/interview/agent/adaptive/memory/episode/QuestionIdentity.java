package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Objects;

/** 题目去重和换场景时必须保持不变的目标，以及两个确定性指纹。 */
public record QuestionIdentity(
    TopicKey topic,
    String evidenceObjective,
    DepthLevel probeDepth,
    String difficulty,
    String scenarioFingerprint,
    String wordingFingerprint
) {

  public QuestionIdentity {
    Objects.requireNonNull(topic, "topic 不能为空");
    Objects.requireNonNull(probeDepth, "probeDepth 不能为空");
    requireText(evidenceObjective, "evidenceObjective");
    requireText(difficulty, "difficulty");
    requireText(scenarioFingerprint, "scenarioFingerprint");
    requireText(wordingFingerprint, "wordingFingerprint");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
  }
}
