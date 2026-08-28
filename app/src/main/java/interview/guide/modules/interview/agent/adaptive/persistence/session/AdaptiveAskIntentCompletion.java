package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;

public record AdaptiveAskIntentCompletion(
    String sessionId,
    String intentId,
    RespondAction action
) {}
