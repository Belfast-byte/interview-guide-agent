package interview.guide.modules.interview.agent.adaptive.runtime;

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
