package interview.guide.modules.interview.agent.adaptive.core;

public record SessionTransition(
    AdaptiveInterviewSession session,
    RespondAction appliedAction
) {}
