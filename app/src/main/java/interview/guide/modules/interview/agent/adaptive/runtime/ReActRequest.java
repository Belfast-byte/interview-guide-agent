package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import java.util.List;

public record ReActRequest(
    String sessionId,
    AgentRole role,
    String llmProvider,
    String jd,
    String resume,
    int maxTurns,
    String dimension,
    String focus,
    List<String> suggestedTools,
    String suggestedSkill,
    List<AdaptiveInterviewTurn> turns,
    CandidateAnswer candidateAnswer
) {

  public ReActRequest {
    suggestedTools = List.copyOf(suggestedTools);
    turns = List.copyOf(turns);
  }

  public int targetTurnIndex() {
    return turns.size() + 1;
  }
}
