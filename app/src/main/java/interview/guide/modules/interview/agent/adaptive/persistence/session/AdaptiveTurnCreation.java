package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;

/**
 * 新轮次持久化参数，避免来源字段在调用点散落。
 */
public record AdaptiveTurnCreation(
    String sessionId,
    int turnIndex,
    int dimensionOrder,
    RespondAction questionAction,
    TurnProvenance provenance
) {
}
