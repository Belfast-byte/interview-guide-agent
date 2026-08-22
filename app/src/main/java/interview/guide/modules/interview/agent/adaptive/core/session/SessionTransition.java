package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;

/**
 * 会话状态迁移记录，描述一次合法的状态变化。
 */
public record SessionTransition(
    AdaptiveInterviewSession session,
    RespondAction appliedAction
) {}
