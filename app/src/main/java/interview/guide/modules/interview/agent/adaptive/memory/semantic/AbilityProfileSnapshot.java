package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.time.LocalDateTime;

/**
 * owner + TopicKey 的不可变能力计数快照。
 */
public record AbilityProfileSnapshot(
    long id,
    MemoryOwner owner,
    TopicKey topic,
    SemanticAbility ability,
    AbilityCounter counter,
    String sourceSessionId,
    AbilityProfileRevisionReason revisionReason,
    LocalDateTime supersededAt,
    LocalDateTime createdAt
) {

  public boolean current() {
    return supersededAt == null;
  }
}
