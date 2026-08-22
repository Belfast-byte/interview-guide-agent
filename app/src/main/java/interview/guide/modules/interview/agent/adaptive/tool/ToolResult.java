package interview.guide.modules.interview.agent.adaptive.tool;

/**
 * 自适应 Agent 工具调用结果密封接口，区分同步完成（CompletedToolResult）与异步进行中（PendingToolResult）。
 */
public sealed interface ToolResult permits CompletedToolResult, PendingToolResult {

  String resultId();

  Object value();

  String summary();
}
