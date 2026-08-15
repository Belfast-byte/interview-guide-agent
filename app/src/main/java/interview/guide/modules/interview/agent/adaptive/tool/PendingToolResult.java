package interview.guide.modules.interview.agent.adaptive.tool;

public record PendingToolResult(
    String handle,
    Object value,
    String summary,
    Integer targetTurnIndex
) implements ToolResult {

  public PendingToolResult(String handle, Object value, String summary) {
    this(handle, value, summary, null);
  }

  @Override
  public String resultId() {
    return handle;
  }
}
