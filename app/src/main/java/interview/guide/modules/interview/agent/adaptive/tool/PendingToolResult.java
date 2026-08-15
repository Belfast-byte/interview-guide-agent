package interview.guide.modules.interview.agent.adaptive.tool;

public record PendingToolResult(
    String handle,
    Object value,
    String summary
) implements ToolResult {

  @Override
  public String resultId() {
    return handle;
  }
}
