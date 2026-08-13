package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;

public record ReActRequest(
    String sessionId,
    AgentRole role,
    String llmProvider,
    InterviewerContext interviewerContext
) {

  public String dimension() {
    return interviewerContext.targetDimension();
  }

  public String suggestedSkill() {
    return interviewerContext.suggestedSkill();
  }

  public int inputTurnIndex() {
    return interviewerContext.currentTurn();
  }

  public int targetTurnIndex() {
    return interviewerContext.currentTurn() + 1;
  }
}
