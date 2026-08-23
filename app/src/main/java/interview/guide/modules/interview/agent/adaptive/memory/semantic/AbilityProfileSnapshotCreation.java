package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Objects;

/**
 * 新能力画像快照的完整事实。
 */
public record AbilityProfileSnapshotCreation(
    MemoryOwner owner,
    TopicKey topic,
    AbilityCounter counter,
    String sourceSessionId,
    AbilityProfileRevisionReason revisionReason
) {

  public AbilityProfileSnapshotCreation {
    Objects.requireNonNull(owner, "owner 不能为空");
    Objects.requireNonNull(topic, "topic 不能为空");
    Objects.requireNonNull(counter, "counter 不能为空");
    Objects.requireNonNull(sourceSessionId, "sourceSessionId 不能为空");
    Objects.requireNonNull(revisionReason, "revisionReason 不能为空");
    if (sourceSessionId.isBlank()) {
      throw new IllegalArgumentException("sourceSessionId 不能为空");
    }
    if (counter.ability().isEmpty()) {
      throw new IllegalArgumentException("空 Counter 不能生成 Profile");
    }
  }
}
