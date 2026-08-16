package interview.guide.modules.interview.agent.adaptive.core;

/**
 * 工具结果事件，表示异步工具执行完成并携带结果。
 */
public record ToolResultEvent(
    int turnIndex,
    String toolName,
    String resultId,
    String summary,
    String output
) implements InterviewInputEvent {}
