package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.assessment.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmAssessmentEvidenceStore;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.SessionTransition;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateAbilityProfileWriter;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService
    implements AlgorithmAssessmentEvidenceStore, CandidateAbilityProfileWriter {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final AdaptiveDimensionBriefRepository dimensionBriefRepository;
  private final CandidateMemoryTopicRepository candidateMemoryTopicRepository;
  private final CandidateMemoryClaimRepository candidateMemoryClaimRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;
  private final PracticeRecordRepository practiceRecordRepository;
  private final AdaptiveAgentToolResultEventRepository toolResultEventRepository;
  private final CandidateAbilityProfileRepository abilityProfileRepository;

  @Transactional
  public boolean reserveToolResultEvent(String sessionId, ToolResultEvent event) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    if (session.status() != AdaptiveSessionStatus.IN_PROGRESS) {
      return false;
    }
    if (toolResultEventRepository.existsByToolNameAndResultId(
        event.toolName(),
        event.resultId()
    )) {
      return false;
    }
    turnRepository.findBySessionIdAndTurnIndex(sessionId, event.turnIndex())
        .orElseThrow();
    toolResultEventRepository.save(new AdaptiveAgentToolResultEventEntity(sessionId, event));
    return true;
  }

  @Transactional
  public void completeToolResultEvent(
      String sessionId,
      ToolResultEvent event,
      RespondAction response,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveAgentToolResultEventEntity entity = toolResultEventRepository
        .findByToolNameAndResultId(event.toolName(), event.resultId())
        .orElseThrow();
    entity.complete(response);
    saveToolExecutions(sessionId, toolExecutions);
  }

  @Transactional
  public void discardToolResultReservation(ToolResultEvent event) {
    toolResultEventRepository.findByToolNameAndResultId(
        event.toolName(),
        event.resultId()
    ).ifPresent(toolResultEventRepository::delete);
  }

  @Transactional(readOnly = true)
  public List<ToolResultFollowUp> toolResultFollowUps(String sessionId) {
    sessionRepository.findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    return toolResultEventRepository.findBySessionIdAndStatusOrderById(
        sessionId,
        ToolResultEventStatus.COMPLETED
    ).stream().map(AdaptiveAgentToolResultEventEntity::toFollowUp).toList();
  }

  @Override
  @Transactional
  public boolean attach(String sessionId, int turnIndex, String executionId) {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository
        .findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElse(null);
    if (assessment == null || evidenceRepository
        .existsByAssessmentIdAndSandboxExecutionId(assessment.id(), executionId)) {
      return false;
    }
    evidenceRepository.save(new AdaptiveAgentEvidenceEntity(
        assessment,
        sessionId,
        turnIndex,
        new ValidatedAssessmentEvidence(
            EvidenceType.TOOL_RESULT,
            null,
            null,
            executionId
        )
    ));
    return true;
  }

  @Transactional(readOnly = true)
  public DepthLevel latestAssessmentDepth(String sessionId, int dimensionOrder) {
    return assessmentRepository
        .findTopBySessionIdAndDimensionOrderOrderByTurnIndexDesc(
            sessionId,
            dimensionOrder
        )
        .orElseThrow(() -> new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "追问缺少上一轮评估事实"
        ))
        .depthLevel();
  }

  @Transactional
  public void replaceAssessment(
      String sessionId,
      int turnIndex,
      AssessmentDecision decision,
      List<ValidatedAssessmentEvidence> evidences
  ) {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository
        .findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "算法判题结果缺少对应的回答评估"
        ));
    assessment.replace(decision);
    evidenceRepository.deleteByAssessmentId(assessment.id());
    evidenceRepository.saveAll(evidences.stream()
        .map(evidence -> new AdaptiveAgentEvidenceEntity(
            assessment,
            sessionId,
            turnIndex,
            evidence
        ))
        .toList());
    AdaptiveAgentTurnEntity sourceTurn = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow();
    saveCodeFactEvidence(assessment, sourceTurn);
    abilityProfileRepository.findBySourceSessionIdAndDimensionOrder(
        sessionId,
        assessment.dimensionOrder()
    ).ifPresent(profile -> profile.replaceAssessment(assessment));
  }

  @Transactional
  public PlannedInterview create(
      String sessionId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    return createInterview(
        null, sessionId, candidateId, jd, resume, llmProvider,
        plan, firstAction, toolExecutions
    );
  }

  @Transactional
  public PlannedInterview createForTenant(
      String tenantId,
      String sessionId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    return createInterview(
        tenantId, sessionId, candidateId, jd, resume, llmProvider,
        plan, firstAction, toolExecutions
    );
  }

  @Transactional
  public PlannedInterview replaceInitialPlan(
      String sessionId,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    AdaptiveAgentTurnEntity firstTurn = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, 1)
        .orElseThrow();
    if (session.status() != AdaptiveSessionStatus.IN_PROGRESS
        || session.toDomain().currentTurn() != 1
        || firstTurn.answer() != null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "只能在回答第一题前刷新项目面试计划");
    }
    turnRepository.delete(firstTurn);
    turnRepository.flush();
    planRepository.deleteAll(planRepository.findBySessionIdOrderByDimensionOrder(sessionId));
    planRepository.flush();
    session.replaceInitialPlan(plan.maxTurns());
    planRepository.saveAll(plan.dimensions().stream()
        .map(dimension -> new AdaptiveAgentPlanEntity(sessionId, dimension))
        .toList());
    turnRepository.save(new AdaptiveAgentTurnEntity(
        sessionId,
        1,
        plan.dimensionForTurn(1).order(),
        firstAction
    ));
    saveToolExecutions(sessionId, toolExecutions);
    return plannedInterview(session, plan);
  }

  private PlannedInterview createInterview(
      String tenantId,
      String sessionId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveInterviewSession session = AdaptiveInterviewSession
        .create(sessionId, plan.maxTurns())
        .start();
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.save(
        new AdaptiveAgentSessionEntity(
            session, tenantId, candidateId, jd, resume, llmProvider
        )
    );
    planRepository.saveAll(plan.dimensions().stream()
        .map(dimension -> new AdaptiveAgentPlanEntity(sessionId, dimension))
        .toList());
    turnRepository.save(new AdaptiveAgentTurnEntity(
        sessionId,
        1,
        plan.dimensionForTurn(1).order(),
        firstAction
    ));
    saveToolExecutions(sessionId, toolExecutions);
    return plannedInterview(sessionEntity, plan);
  }

  @Transactional
  public PlannedInterview recordDecision(
      String sessionId,
      CandidateAnswer answer,
      RespondAction proposedAction,
      List<ToolExecution> toolExecutions,
      DimensionBrief dimensionBrief,
      List<CandidateClaim> candidateClaims,
      AssessmentDecision assessmentDecision,
      List<ValidatedAssessmentEvidence> assessmentEvidences,
      List<PracticeRecommendation> practiceRecommendations
  ) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    List<AdaptiveAgentPlanEntity> planEntities = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId);
    InterviewPlan plan = toPlan(sessionId, sessionEntity.toDomain().maxTurns(), planEntities);
    if (!plan.isLastTurn(answer.turnIndex())
        && proposedAction.type() == AgentResponseType.FINISH) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "全部规划维度覆盖前不能结束面试");
    }
    InterviewPlan updatedPlan = plan.answer(answer.turnIndex());
    PlannedDimension answeredDimension = updatedPlan.dimensionForTurn(answer.turnIndex());
    SessionTransition transition = sessionEntity.toDomain().apply(answer, proposedAction);
    AdaptiveAgentTurnEntity turnEntity = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, answer.turnIndex())
        .orElseThrow();

    turnEntity.complete(answer, transition.appliedAction());
    sessionEntity.apply(transition.session());
    for (int index = 0; index < planEntities.size(); index++) {
      planEntities.get(index).apply(updatedPlan.dimensions().get(index));
    }
    sessionRepository.flush();

    if (transition.appliedAction().type() == AgentResponseType.ASK) {
      turnRepository.save(new AdaptiveAgentTurnEntity(
          sessionId,
          transition.session().currentTurn(),
          updatedPlan.dimensionForTurn(transition.session().currentTurn()).order(),
          transition.appliedAction()
      ));
    }
    saveToolExecutions(sessionId, toolExecutions);
    if (dimensionBrief != null) {
      dimensionBriefRepository.save(new AdaptiveDimensionBriefEntity(dimensionBrief));
    }
    if (answeredDimension.status() == PlanDimensionStatus.COMPLETED) {
      candidateMemoryTopicRepository.save(new CandidateMemoryTopicEntity(
          sessionEntity.tenantId(),
          sessionEntity.candidateId(),
          sessionId,
          answeredDimension
      ));
    }
    candidateMemoryClaimRepository.saveAll(candidateClaims.stream()
        .map(claim -> new CandidateMemoryClaimEntity(
            sessionEntity.tenantId(),
            sessionEntity.candidateId(),
            sessionId,
            claim
        ))
        .toList());
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.save(
        new AdaptiveAgentAssessmentEntity(answeredDimension.order(), assessmentDecision)
    );
    evidenceRepository.saveAll(assessmentEvidences.stream()
        .map(evidence -> new AdaptiveAgentEvidenceEntity(
            assessment,
            sessionId,
            answer.turnIndex(),
            evidence
        ))
        .toList());
    saveCodeFactEvidence(assessment, turnEntity);
    practiceRecordRepository.saveAll(practiceRecommendations.stream()
        .map(recommendation -> new PracticeRecordEntity(
            sessionEntity,
            recommendation
        ))
        .toList());
    if (transition.session().status() == AdaptiveSessionStatus.COMPLETED) {
      refreshProfiles(sessionEntity, planEntities);
    }
    return plannedInterview(sessionEntity, updatedPlan);
  }

  @Override
  @Transactional
  public void refresh(String sessionId) {
    AdaptiveAgentSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    refreshProfiles(
        session,
        planRepository.findBySessionIdOrderByDimensionOrder(sessionId)
    );
  }

  private void refreshProfiles(
      AdaptiveAgentSessionEntity session,
      List<AdaptiveAgentPlanEntity> dimensions
  ) {
    if (session.status() != AdaptiveSessionStatus.COMPLETED) {
      return;
    }
    for (AdaptiveAgentPlanEntity dimension : dimensions) {
      AdaptiveAgentAssessmentEntity assessment = assessmentRepository
          .findTopBySessionIdAndDimensionOrderOrderByTurnIndexDesc(
              session.id(),
              dimension.dimensionOrder()
          )
          .orElseThrow();
      CandidateAbilityProfileEntity existing = abilityProfileRepository
          .findBySourceSessionIdAndDimensionOrder(
              session.id(),
              dimension.dimensionOrder()
          )
          .orElse(null);
      if (existing != null) {
        existing.replaceAssessment(assessment);
        continue;
      }
      CandidateAbilityProfileEntity current = session.tenantId() == null
          ? abilityProfileRepository
              .findByTenantIdIsNullAndCandidateIdAndDimensionAndSupersededByIsNull(
                  session.candidateId(),
                  dimension.dimension()
              )
              .orElse(null)
          : abilityProfileRepository
              .findByTenantIdAndCandidateIdAndDimensionAndSupersededByIsNull(
                  session.tenantId(),
                  session.candidateId(),
                  dimension.dimension()
              )
              .orElse(null);
      CandidateAbilityProfileEntity profile = abilityProfileRepository.save(
          new CandidateAbilityProfileEntity(session, dimension, assessment)
      );
      if (current != null && !current.sourceSessionId().equals(session.id())) {
        if (current.createdAt().isBefore(profile.createdAt())) {
          current.supersede(profile.id());
        } else {
          profile.supersede(current.id());
        }
      }
    }
  }

  private void saveCodeFactEvidence(
      AdaptiveAgentAssessmentEntity assessment,
      AdaptiveAgentTurnEntity turn
  ) {
    if (turn.codeFactUsage() != null) {
      evidenceRepository.save(new AdaptiveAgentEvidenceEntity(
          assessment,
          assessment.sessionId(),
          turn.turnIndex(),
          turn.codeSourceId(),
          turn.codeAnchor(),
          turn.codeFactUsage()
      ));
    }
  }

  @Transactional(readOnly = true)
  public PlannedInterview get(String sessionId) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    InterviewPlan plan = toPlan(
        sessionId,
        sessionEntity.toDomain().maxTurns(),
        planRepository.findBySessionIdOrderByDimensionOrder(sessionId)
    );
    return plannedInterview(sessionEntity, plan);
  }

  @Transactional(readOnly = true)
  public PlannedInterview getForTenant(String tenantId, String sessionId) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository
        .findByIdAndTenantId(sessionId, tenantId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    InterviewPlan plan = toPlan(
        sessionId,
        sessionEntity.toDomain().maxTurns(),
        planRepository.findBySessionIdOrderByDimensionOrder(sessionId)
    );
    return plannedInterview(sessionEntity, plan);
  }

  private PlannedInterview plannedInterview(
      AdaptiveAgentSessionEntity sessionEntity,
      InterviewPlan plan
  ) {
    return new PlannedInterview(
        history(sessionEntity),
        plan,
        dimensionBriefRepository
            .findBySessionIdOrderByDimensionOrder(sessionEntity.id())
            .stream()
            .map(AdaptiveDimensionBriefEntity::toDomain)
            .toList()
    );
  }

  private void saveToolExecutions(
      String sessionId,
      List<ToolExecution> toolExecutions
  ) {
    toolCallRepository.saveAll(toolExecutions.stream()
        .map(execution -> new AdaptiveAgentToolCallEntity(sessionId, execution))
        .toList());
  }

  private InterviewPlan toPlan(
      String sessionId,
      int maxTurns,
      List<AdaptiveAgentPlanEntity> entities
  ) {
    return new InterviewPlan(
        sessionId,
        maxTurns,
        entities.stream().map(AdaptiveAgentPlanEntity::toDomain).toList()
    );
  }

  private AdaptiveInterviewHistory history(AdaptiveAgentSessionEntity sessionEntity) {
    AdaptiveInterviewSession session = sessionEntity.toDomain();
    return new AdaptiveInterviewHistory(
        session,
        sessionEntity.candidateId(),
        sessionEntity.jd(),
        sessionEntity.resume(),
        sessionEntity.llmProvider(),
        turnRepository.findBySessionIdOrderByTurnIndex(session.id()).stream()
            .map(AdaptiveAgentTurnEntity::toDomain)
            .toList()
    );
  }
}
