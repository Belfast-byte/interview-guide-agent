package interview.guide.modules.interview.agent.model;

import interview.guide.modules.interview.agent.runtime.AgentLoopState;
import interview.guide.modules.interview.agent.runtime.AgentLoopStatus;
import java.util.List;

public record AgentInterviewSessionResponse(
    String sessionId,
    String runtimeVersion,
    int currentTurn,
    int maxTurns,
    AgentLoopStatus status,
    String selectedSkillId,
    String selectedSkillHash,
    String currentQuestion,
    String finishReason,
    List<AgentInterviewTurnResponse> turns
) {

  public static AgentInterviewSessionResponse from(AgentLoopState state) {
    return new AgentInterviewSessionResponse(
        state.sessionId(),
        state.runtimeVersion(),
        state.currentTurn(),
        state.maxTurns(),
        state.status(),
        state.loadedSkill() == null ? null : state.loadedSkill().id(),
        state.loadedSkill() == null ? null : state.loadedSkill().hash(),
        state.currentQuestion(),
        state.finishReason(),
        state.turns().stream()
            .map(AgentInterviewTurnResponse::from)
            .toList()
    );
  }
}
