package interview.guide.modules.interview.agent.adaptive.core;

public record ToolResultEvent(
    int turnIndex,
    String toolName,
    String resultId,
    String summary,
    String output
) implements InterviewInputEvent {}
