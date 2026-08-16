package interview.guide.modules.interview.agent.adaptive.runtime;

/**
 * 单次工具执行记录，包含调用参数、结果 ID、输出和是否成功。
 */
public record ToolExecution(
    String invocationId,
    String toolName,
    String reason,
    String role,
    int turnIndex,
    String inputSummary,
    String outputSummary,
    String resultId,
    String output,
    ToolExecutionOutcome outcome,
    long durationMillis
) {

  public ToolExecution(
      String invocationId,
      String toolName,
      String reason,
      String role,
      int turnIndex,
      String inputSummary,
      String outputSummary,
      String resultId,
      String output,
      long durationMillis
  ) {
    this(
        invocationId,
        toolName,
        reason,
        role,
        turnIndex,
        inputSummary,
        outputSummary,
        resultId,
        output,
        ToolExecutionOutcome.COMPLETED,
        durationMillis
    );
  }
}
