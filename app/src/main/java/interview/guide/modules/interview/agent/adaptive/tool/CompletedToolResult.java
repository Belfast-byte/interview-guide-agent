package interview.guide.modules.interview.agent.adaptive.tool;

/**
 * 同步完成的工具结果。
 */
public record CompletedToolResult(
    String resultId,
    Object value,
    String summary
) implements ToolResult {}
