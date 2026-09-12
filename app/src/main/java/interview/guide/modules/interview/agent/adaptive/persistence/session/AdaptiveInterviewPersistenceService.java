package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn.AssessmentFeedback;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import java.util.Map;
import java.util.stream.Collectors;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
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
  private final CoverageQueryService coverageQueryService;
  private final AdaptiveAgentAssessmentRepository assessments;
  private final AdaptiveAgentEvidenceRepository evidences;

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
        coverageQueryService.load(plan, history.turns())
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
        turnsWithAssessments(session.id()),
        entity.failureReason()
    );
  }

  private List<AdaptiveInterviewTurn> turnsWithAssessments(String sessionId) {
    var quotes = evidences.findReportEvidence(sessionId).stream()
        .filter(evidence -> evidence.evidenceType() == EvidenceType.QUOTE)
        .collect(Collectors.groupingBy(evidence -> evidence.assessment().turnIndex(),
            Collectors.mapping(evidence -> SourceQuote.fromStored(evidence.quoteText(), evidence.quoteLocator()),
                Collectors.toList())));
    Map<Integer, AssessmentFeedback> feedback = assessments
        .findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(sessionId).stream()
        .collect(Collectors.toMap(assessment -> assessment.turnIndex(), assessment -> new AssessmentFeedback(
            assessment.depthLevel(), assessment.rationaleSummary(), assessment.codeReview(),
            quotes.getOrDefault(assessment.turnIndex(), List.of()))));
    return turnRepository.findBySessionIdOrderByTurnIndex(sessionId).stream()
        .map(turn -> turn.toDomain().withAssessmentFeedback(feedback.get(turn.turnIndex()))).toList();
  }

  @Transactional(readOnly = true)
  public interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer answerForCandidate(
      String candidateId, String sessionId, int turnIndex) {
    sessionRepository.findByIdAndCandidateIdAndTenantIdIsNull(sessionId, candidateId).orElseThrow(this::notFound);
    var turn = turnRepository.findBySessionIdAndTurnIndex(sessionId, turnIndex).orElseThrow(this::notFound);
    if (!turn.hasAnswer()) throw new BusinessException(ErrorCode.BAD_REQUEST, "当前轮次尚未提交答案");
    return turn.candidateAnswer();
  }

  private BusinessException notFound() {
    return new BusinessException(
        ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
        "Agent 面试会话不存在"
    );
  }
}
