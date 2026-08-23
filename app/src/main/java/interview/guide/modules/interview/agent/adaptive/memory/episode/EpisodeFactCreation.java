package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Objects;

/**
 * 创建最小 Episode 事实所需的权威索引。
 */
public record EpisodeFactCreation(
    MemoryOwner owner,
    String sessionId,
    int turnIndex,
    TopicKey topic
) {

  public EpisodeFactCreation {
    Objects.requireNonNull(owner, "owner 不能为空");
    Objects.requireNonNull(sessionId, "sessionId 不能为空");
    Objects.requireNonNull(topic, "topic 不能为空");
    if (sessionId.isBlank() || turnIndex < 1) {
      throw new IllegalArgumentException("Episode sessionId 和 turnIndex 非法");
    }
  }
}
