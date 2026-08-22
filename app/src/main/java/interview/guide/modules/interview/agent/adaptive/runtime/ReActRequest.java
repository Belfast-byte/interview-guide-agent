package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;

/**
 * ReAct 执行请求，携带会话、角色、Prompt 上下文和工具白名单。
 */
public record ReActRequest(
    String sessionId,
    AgentRole role,
    String llmProvider,
    InterviewerContext interviewerContext
) {

  public int inputTurnIndex() {
    if (interviewerContext.currentToolResult() != null) {
      return interviewerContext.currentToolResult().turnIndex();
    }
    return interviewerContext.currentTurn();
  }

  public int targetTurnIndex() {
    if (interviewerContext.currentToolResult() != null) {
      return interviewerContext.currentToolResult().turnIndex();
    }
    return interviewerContext.currentTurn() + 1;
  }
}
