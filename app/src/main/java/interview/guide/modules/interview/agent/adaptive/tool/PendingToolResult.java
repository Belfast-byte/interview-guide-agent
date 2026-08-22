package interview.guide.modules.interview.agent.adaptive.tool;

/**
 * 异步进行中的工具结果。
 */
public record PendingToolResult(
    String handle,
    Object value,
    String summary,
    Integer targetTurnIndex
) implements ToolResult {

  @Override
  public String resultId() {
    return handle;
  }
}
