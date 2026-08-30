package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 自适应面试查询服务；正式推进事实由创建和回答事务服务提交。 */
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveDimensionBriefRepository dimensionBriefRepository;
  private final CoverageQueryService coverageQueryService;

  @Transactional(readOnly = true)
  public void requireCandidateSession(String candidateId, String sessionId) {
    sessionRepository.findByIdAndCandidateIdAndTenantIdIsNull(sessionId, candidateId)
        .orElseThrow(this::notFound);
  }

  @Transactional(readOnly = true)
  public PlannedInterview get(String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(this::notFound);
    return plannedInterview(session, plan(session));
  }

  @Transactional(readOnly = true)
  public PlannedInterview getForTenant(String tenantId, String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantId(sessionId, tenantId)
        .orElseThrow(this::notFound);
    return plannedInterview(session, plan(session));
  }

  private InterviewPlan plan(AdaptiveAgentSessionEntity session) {
    return new InterviewPlan(
        session.id(),
        session.toDomain().maxTurns(),
        planRepository.findBySessionIdOrderByDimensionOrder(session.id()).stream()
            .map(AdaptiveAgentPlanEntity::toDomain)
            .toList()
    );
  }

  private PlannedInterview plannedInterview(
      AdaptiveAgentSessionEntity session,
      InterviewPlan plan
  ) {
    AdaptiveInterviewHistory history = history(session);
    return new PlannedInterview(
        history,
        plan,
        coverageQueryService.load(plan, history.turns()),
        dimensionBriefRepository.findBySessionIdOrderByDimensionOrder(session.id()).stream()
            .map(AdaptiveDimensionBriefEntity::toDomain)
            .toList()
    );
  }

  private AdaptiveInterviewHistory history(AdaptiveAgentSessionEntity entity) {
    AdaptiveInterviewSession session = entity.toDomain();
    return new AdaptiveInterviewHistory(
        session,
        entity.candidateId(),
        entity.jd(),
        entity.resume(),
        entity.llmProvider(),
        entity.llmProviderNameSnapshot(),
        entity.llmModelSnapshot(),
        turnRepository.findBySessionIdOrderByTurnIndex(session.id()).stream()
            .map(AdaptiveAgentTurnEntity::toDomain)
            .toList(),
        entity.failureReason()
    );
  }

  private BusinessException notFound() {
    return new BusinessException(
        ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
        "Agent 面试会话不存在"
    );
  }
}
