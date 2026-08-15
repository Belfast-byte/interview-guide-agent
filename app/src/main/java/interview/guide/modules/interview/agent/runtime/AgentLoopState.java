package interview.guide.modules.interview.agent.runtime;

import java.util.List;

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
