package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import java.util.Objects;

/**
 * 一次下一题决策使用的不可变工作记忆。
 */
public record WorkingMemorySnapshot(
    String sessionId,
    int currentTurnIndex,
    TopicKey currentTopic,
    ProbeGap selectedGap,
    int followUpDepth,
    TurnTriggerType triggerType
) {

  public WorkingMemorySnapshot {
    Objects.requireNonNull(sessionId, "sessionId 不能为空");
    Objects.requireNonNull(currentTopic, "currentTopic 不能为空");
    Objects.requireNonNull(triggerType, "triggerType 不能为空");
    if (currentTurnIndex < 1 || followUpDepth < 0) {
      throw new IllegalArgumentException("轮次和追问深度不能为负");
    }
  }
}
