package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 报告中的工具结果。
 */
public record ReportToolResult(
    Long toolCallId,
    String sandboxExecutionId,
    String toolName,
    String resultId,
    String output
) {

  public ReportToolResult(
      long toolCallId,
      String toolName,
      String resultId,
      String output
  ) {
    this(toolCallId, null, toolName, resultId, output);
  }
}
