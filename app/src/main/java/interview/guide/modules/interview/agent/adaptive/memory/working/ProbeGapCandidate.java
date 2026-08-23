package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Objects;

/**
 * 可用于追问的已持久化 Assessment gap。
 */
public record ProbeGapCandidate(
    long id,
    Long assessmentId,
    int sourceTurnIndex,
    TopicKey topic,
    int gapOrder,
    ProbeGap gap
) {

  public ProbeGapCandidate {
    if (id < 1 || (assessmentId != null && assessmentId < 1)
        || sourceTurnIndex < 1 || gapOrder < 1) {
      throw new IllegalArgumentException("ProbeGap 标识和顺序必须为正数");
    }
    Objects.requireNonNull(topic, "topic 不能为空");
    Objects.requireNonNull(gap, "gap 不能为空");
  }
}
