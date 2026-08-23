package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import java.util.List;

/**
 * 组装单次下一题决策工作记忆所需的事实。
 */
public record WorkingMemoryInput(
    String sessionId,
    int currentTurnIndex,
    TopicKey currentTopic,
    Integer parentTurnIndex,
    TurnTriggerType triggerType,
    List<ProbeGap> probeGaps,
    List<AdaptiveInterviewTurn> history
) {

  public WorkingMemoryInput {
    probeGaps = List.copyOf(probeGaps);
    history = List.copyOf(history);
  }
}
