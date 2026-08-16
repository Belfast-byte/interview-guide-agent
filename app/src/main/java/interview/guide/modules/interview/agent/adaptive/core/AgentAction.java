package interview.guide.modules.interview.agent.adaptive.core;

/**
 * Agent 可执行动作的密封接口，当前只允许回复（RespondAction）或调用工具（ToolCallAction）。
 */
public sealed interface AgentAction permits RespondAction, ToolCallAction {

  String reason();
}
