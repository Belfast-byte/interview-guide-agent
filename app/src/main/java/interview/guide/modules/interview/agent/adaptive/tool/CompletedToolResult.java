package interview.guide.modules.interview.agent.adaptive.tool;

public record CompletedToolResult(
    String resultId,
    Object value,
    String summary
) implements ToolResult {}
