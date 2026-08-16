package interview.guide.modules.interview.agent.adaptive.core;

/**
 * 会话状态迁移记录，描述一次合法的状态变化。
 */
public record SessionTransition(
    AdaptiveInterviewSession session,
    RespondAction appliedAction
) {}
