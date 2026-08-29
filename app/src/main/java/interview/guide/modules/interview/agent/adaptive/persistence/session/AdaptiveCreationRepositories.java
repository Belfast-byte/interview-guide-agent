package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 创建用例所需的三个聚合 Repository，避免事务服务依赖回答链的大持久化门面。 */
@Component
class AdaptiveCreationRepositories {

  private final AdaptiveAgentSessionRepository sessions;
  private final AdaptiveAgentPlanRepository plans;
  private final AdaptiveAgentTurnRepository turns;

  AdaptiveCreationRepositories(
      AdaptiveAgentSessionRepository sessions,
      AdaptiveAgentPlanRepository plans,
      AdaptiveAgentTurnRepository turns
  ) {
    this.sessions = sessions;
    this.plans = plans;
    this.turns = turns;
  }

  Optional<AdaptiveAgentSessionEntity> session(String sessionId) {
    return sessions.findById(sessionId);
  }

  AdaptiveAgentSessionEntity saveSession(AdaptiveAgentSessionEntity session) {
    return sessions.save(session);
  }

  void savePlans(List<AdaptiveAgentPlanEntity> entities) {
    plans.saveAll(entities);
  }

  Optional<AdaptiveAgentTurnEntity> turn(String sessionId, int turnIndex) {
    return turns.findBySessionIdAndTurnIndex(sessionId, turnIndex);
  }

  AdaptiveAgentTurnEntity saveTurn(AdaptiveAgentTurnEntity turn) {
    return turns.saveAndFlush(turn);
  }
}
