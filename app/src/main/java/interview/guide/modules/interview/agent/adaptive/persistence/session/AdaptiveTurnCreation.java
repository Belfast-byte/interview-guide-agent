package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;

/**
 * 新轮次持久化参数，避免来源字段在调用点散落。
 */
record AdaptiveTurnCreation(
    String sessionId,
    int turnIndex,
    int dimensionOrder,
    RespondAction questionAction,
    TurnProvenance provenance
) {

  static AdaptiveTurnCreation initial(
      String sessionId,
      int dimensionOrder,
      RespondAction questionAction
  ) {
    return new AdaptiveTurnCreation(
        sessionId,
        1,
        dimensionOrder,
        questionAction,
        TurnProvenance.initial()
    );
  }
}
