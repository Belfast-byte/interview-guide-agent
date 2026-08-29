package interview.guide.modules.interview.agent.adaptive.persistence.session;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** 回答最终事务中的 Session/Turn 锁与写入。 */
@Component
class AdaptiveAnswerCoreRepositories {

  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentTurnRepository turns;

  AdaptiveAnswerCoreRepositories(
      AdaptiveAgentSessionRepository sessions,
      AdaptiveAgentTurnRepository turns
  ) {
    this.sessions = sessions;
    this.turns = turns;
  }

  Optional<AdaptiveAgentSessionEntity> lockedSession(String sessionId) {
    return sessions.findLockedById(sessionId);
  }

  Optional<AdaptiveAgentTurnEntity> lockedTurn(String sessionId, int turnIndex) {
    return turns.findLockedBySessionIdAndTurnIndex(sessionId, turnIndex);
  }

  AdaptiveAgentTurnEntity saveTurn(AdaptiveAgentTurnEntity turn) {
    return turns.saveAndFlush(turn);
  }
}
