package interview.guide.modules.interview.agent.runtime;

import java.util.List;

/**
 * Agent 面试循环的不可变状态快照，包含会话标识、JD/简历、当前轮次、已发生轮次和结束原因。
 */
public record AgentLoopState(
    String sessionId,
    String runtimeVersion,
    String jd,
    String resume,
    int currentTurn,
    int maxTurns,
    LoadedSkill loadedSkill,
    List<Turn> turns,
    AgentLoopStatus status,
    String finishReason
) {

  public AgentLoopState {
    turns = List.copyOf(turns);
  }

  public String currentQuestion() {
    if (turns.isEmpty()) {
      return null;
    }
    Turn lastTurn = turns.getLast();
    return lastTurn.answer() == null ? lastTurn.question() : null;
  }
}
