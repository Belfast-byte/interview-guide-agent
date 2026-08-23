package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceStore;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionTransition;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateAbilityProfileRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryClaimRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTopicEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.CandidateMemoryTopicRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自适应面试持久化服务，统一读写会话、计划、轮次、工具调用、评估、记忆与练习记录。
 */
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService
    implements AlgorithmAssessmentEvidenceStore {

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

  @Transactional(readOnly = true)
  public void requireCandidateSession(String candidateId, String sessionId) {
    sessionRepository.findByIdAndCandidateIdAndTenantIdIsNull(sessionId, candidateId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
  }

  /**
   * 预留工具结果事件。并发重复投递撞 uk_agent_tool_result_event 唯一约束时抛
   * {@link org.springframework.dao.DataIntegrityViolationException}，由应用层转「已存在」语义。
   *
   * @return true 表示预留成功；false 表示会话尚在 CREATED 骨架期，不接受工具事件
   */
  @Transactional
  public boolean reserveToolResultEvent(String sessionId, ToolResultEvent event) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    if (session.status() == AdaptiveSessionStatus.CREATED) {
      return false;
    }
    turnRepository.findBySessionIdAndTurnIndex(sessionId, event.turnIndex())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "面试轮次不存在"
        ));
    toolResultEventRepository.saveAndFlush(
        new AdaptiveAgentToolResultEventEntity(sessionId, event)
    );
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
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "工具结果事件不存在"
        ));
    entity.complete(response);
    applyFollowUpQuestion(sessionId, event, response);
    saveToolExecutions(sessionId, toolExecutions);
  }

  /**
   * 把基于工具结果的追问落为面试问题：结果属于更早的已答轮次时（完整判题/补丁），
   * 当前待答轮次的问题是在判题结果未知时生成的占位问题，用追问替换它，使候选人
   * 回答追问时评估上下文使用追问本身；结果属于当前待答轮次（公开样例试跑）或会话
   * 已结束（最后一轮提交后判题才返回）时，只保留追问事件记录。
   */
  private void applyFollowUpQuestion(
      String sessionId,
      ToolResultEvent event,
      RespondAction followUp
  ) {
    AdaptiveAgentSessionEntity session = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    if (session.status() != AdaptiveSessionStatus.IN_PROGRESS) {
      return;
    }
    int currentTurn = session.toDomain().currentTurn();
    if (event.turnIndex() == currentTurn) {
      return;
    }
    turnRepository
        .findBySessionIdAndTurnIndex(sessionId, currentTurn)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"))
        .replaceQuestion(followUp);
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
            ErrorCode.NOT_FOUND,
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
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
    saveCodeFactEvidence(assessment, sourceTurn);
    abilityProfileRepository.findBySourceSessionIdAndDimensionOrder(
        sessionId,
        assessment.dimensionOrder()
    ).ifPresent(profile -> profile.replaceAssessment(assessment));
  }

  /**
   * 非末轮维度完成的记忆落库：由异步任务调用，仅存维度小结与候选人声明，
   * 与答题主路径解耦；调用方已保证该维度确已完成。
   */
  @Transactional
  public void saveDimensionMemory(
      String sessionId,
      DimensionBrief brief,
      List<CandidateClaim> claims
  ) {
    dimensionBriefRepository.save(new AdaptiveDimensionBriefEntity(brief));
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository
        .findByIdAndTenantIdIsNull(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    candidateMemoryClaimRepository.saveAll(claims.stream()
        .filter(claim -> claim.skillId() != null && !claim.skillId().isBlank()
            && claim.focusId() != null && !claim.focusId().isBlank())
        .distinct()
        .map(claim -> new CandidateMemoryClaimEntity(
            sessionEntity.tenantId(),
            sessionEntity.candidateId(),
            sessionId,
            claim
        ))
        .toList());
  }

  /**
   * 落 CREATED 骨架会话：异步创建链路的第一步，立即对前端可见；轮次预算为占位值，
   * 规划完成后由 {@link #completeCreation} 回填。
   */
  @Transactional
  public PlannedInterview createSkeleton(AdaptiveSessionCreation creation) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.save(
        new AdaptiveAgentSessionEntity(
            AdaptiveInterviewSession.create(
                creation.sessionId(),
                AdaptiveInterviewSession.MAX_TURNS
            ),
            creation
        )
    );
    return plannedInterview(
        sessionEntity,
        new InterviewPlan(creation.sessionId(), 0, List.of())
    );
  }

  /**
   * 创建链路完成：回填真实轮次预算、落计划与首题并推进 IN_PROGRESS。
   */
  @Transactional
  public PlannedInterview completeCreation(
      String sessionId,
      InterviewPlan plan,
      RespondAction firstAction,
      List<ToolExecution> toolExecutions
  ) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    AdaptiveInterviewSession started = sessionEntity.toDomain().start();
    sessionEntity.apply(new AdaptiveInterviewSession(
        started.id(),
        started.runtimeVersion(),
        started.status(),
        started.currentTurn(),
        plan.maxTurns()
    ));
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

  /**
   * 创建链路失败：把 CREATED 骨架置为 FAILED 并记录可读原因；非创建态会话原样保留。
   */
  @Transactional
  public void failCreation(String sessionId, String reason) {
    sessionRepository.findById(sessionId)
        .ifPresent(entity -> entity.markFailed(reason));
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
    if (assessmentDecision.recommendsEarlyCompletion()) {
      plan = plan.completeDimensionEarly(answer.turnIndex());
    }
    InterviewPlan updatedPlan = plan.answer(answer.turnIndex());
    PlannedDimension answeredDimension = updatedPlan.dimensionForTurn(answer.turnIndex());
    SessionTransition transition = sessionEntity.toDomain().apply(answer, proposedAction);
    AdaptiveAgentTurnEntity turnEntity = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, answer.turnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));

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
        .filter(claim -> claim.skillId() != null && !claim.skillId().isBlank()
            && claim.focusId() != null && !claim.focusId().isBlank())
        .distinct()
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
          .orElse(null);
      if (assessment == null) {
        // 模型提前结束面试时，未开考维度没有评估事实，跳过画像刷新
        continue;
      }
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
    AdaptiveAgentEvidenceEntity.codeFact(
        assessment,
        assessment.sessionId(),
        turn.turnIndex(),
        turn.codeSourceId(),
        turn.codeAnchor(),
        turn.codeFactUsage()
    ).ifPresent(evidenceRepository::save);
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
        sessionEntity.llmProviderNameSnapshot(),
        sessionEntity.llmModelSnapshot(),
        turnRepository.findBySessionIdOrderByTurnIndex(session.id()).stream()
            .map(AdaptiveAgentTurnEntity::toDomain)
            .toList(),
        sessionEntity.failureReason()
    );
  }
}
