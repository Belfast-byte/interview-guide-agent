package interview.guide.modules.interview.agent.adaptive.core.intent;

public record ActionIntentProgress(
    ActionIntentStatus status,
    ActionIntentOutcome outcome,
    ActionIntentTiming timing
) {}
