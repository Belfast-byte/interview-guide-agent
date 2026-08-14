package interview.guide.modules.interview.agent.adaptive.assessment;

public record ReportToolResult(
    long toolCallId,
    String toolName,
    String resultId,
    String output
) {}
