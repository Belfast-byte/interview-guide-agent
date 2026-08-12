package interview.guide.modules.interview.agent.adaptive.core;

public sealed interface AgentAction permits RespondAction, ToolCallAction {

  String reason();
}
