package interview.guide.modules.interview.agent.adaptive.core.intent;

public record ActionIntentKey(
    String intentId,
    String sessionId,
    long basedOnRevision
) {}
