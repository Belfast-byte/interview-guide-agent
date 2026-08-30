package interview.guide.modules.interview.agent.adaptive.assessment.report;

/**
 * 报告中的工具结果。
 */
public record ReportToolResult(
    String sandboxExecutionId,
    String toolName,
    String resultId,
    String output
) {}
