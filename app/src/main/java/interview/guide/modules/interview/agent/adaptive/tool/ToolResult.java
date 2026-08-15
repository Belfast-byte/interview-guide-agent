package interview.guide.modules.interview.agent.adaptive.tool;

public sealed interface ToolResult permits CompletedToolResult, PendingToolResult {

  String resultId();

  Object value();

  String summary();
}
