package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.util.Objects;

/**
 * 创建最小 Episode 事实所需的权威索引。
 */
public record EpisodeFactCreation(
    MemoryOwner owner,
    String sessionId,
    SessionMode sessionMode,
    long turnId,
    int turnIndex,
    TopicKey topic,
    String targetId,
    EpisodeAssistanceLevel assistanceLevel,
    EpisodeClosureStatus closureStatus,
    Long correctsEpisodeId
) {

  public EpisodeFactCreation {
    Objects.requireNonNull(owner, "owner 不能为空");
    Objects.requireNonNull(sessionId, "sessionId 不能为空");
    Objects.requireNonNull(sessionMode, "sessionMode 不能为空");
    Objects.requireNonNull(topic, "topic 不能为空");
    Objects.requireNonNull(targetId, "targetId 不能为空");
    Objects.requireNonNull(assistanceLevel, "assistanceLevel 不能为空");
    Objects.requireNonNull(closureStatus, "closureStatus 不能为空");
    if (sessionId.isBlank() || targetId.isBlank() || turnId < 1 || turnIndex < 1) {
      throw new IllegalArgumentException("Episode sessionId 和 turnIndex 非法");
    }
    if (correctsEpisodeId != null && correctsEpisodeId < 1) {
      throw new IllegalArgumentException("Episode 纠正引用非法");
    }
  }
}
