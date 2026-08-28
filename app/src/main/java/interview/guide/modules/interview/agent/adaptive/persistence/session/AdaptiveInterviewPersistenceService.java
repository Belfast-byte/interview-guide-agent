package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionTransition;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AdaptiveDimensionBriefRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodePersistenceInput;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.practice.PracticeRecordRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自适应面试持久化服务，统一读写会话、计划、轮次、工具调用、评估、记忆与练习记录。
 */
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewPersistenceService {

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final AdaptiveDimensionBriefRepository dimensionBriefRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AdaptiveAgentEvidenceRepository evidenceRepository;
  private final AssessmentProbeGapRepository probeGapRepository;
  private final PracticeRecordRepository practiceRecordRepository;
  private final AdaptiveAgentToolResultEventRepository toolResultEventRepository;
  private final EpisodeFactPersistence episodeFactPersistence;
  private final WorkStatePersistenceService workStatePersistenceService;
  private final ActionIntentPersistenceService actionIntentPersistenceService;

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
      WorkStatePatch workStatePatch
  ) {
    AdaptiveAgentToolResultEventEntity entity = toolResultEventRepository
        .findBySessionIdAndToolNameAndResultId(
            sessionId,
            event.toolName(),
            event.resultId()
        )
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "工具结果事件不存在"
        ));
    entity.complete();
    workStatePersistenceService.apply(workStatePatch);
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

  /** 非末轮维度完成后异步保存维度小结。 */
  @Transactional
  public void saveDimensionBrief(DimensionBrief brief) {
    dimensionBriefRepository.save(new AdaptiveDimensionBriefEntity(brief));
  }

  /**
   * 落 CREATED 骨架会话：异步创建链路的第一步，立即对前端可见；轮次预算为占位值，
   * 规划完成后回填真实轮次预算。
   */
  @Transactional
  public PlannedInterview createSkeleton(AdaptiveSessionCreation creation) {
    AdaptiveAgentSessionEntity sessionEntity = sessionRepository.save(
        new AdaptiveAgentSessionEntity(
            AdaptiveInterviewSession.create(
                creation.sessionId(),
                AdaptiveInterviewSession.MAX_TURNS,
                creation.settings()
            ),
            creation
        )
    );
    return plannedInterview(
        sessionEntity,
        new InterviewPlan(creation.sessionId(), 0, List.of())
    );
  }

  @Transactional
  public PlannedInterview completePreparedAnswer(
      String sessionId,
      RespondAction action,
      List<WorkStatePatch> policyPatches
  ) {
    AdaptiveAgentSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    AdaptiveInterviewSession current = session.toDomain();
    AdaptiveAgentTurnEntity turn = answeredTurn(sessionId, current.currentTurn());
    CandidateAnswer answer = turn.candidateAnswer();
    SessionTransition transition = current.apply(answer, action);
    turn.recordResponse(transition.appliedAction());
    session.apply(transition.session());
    applyPatches(policyPatches, workStatePersistenceService.get(sessionId));
    List<AdaptiveAgentPlanEntity> plans = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId);
    return plannedInterview(session, toPlan(sessionId, current.maxTurns(), plans));
  }

  @Transactional
  public PlannedInterview prepareAction(AdaptiveActionPreparationInput input) {
    AdaptiveAnswerFacts answerFacts = input.answer();
    AdaptiveAgentSessionEntity session = findOwnedSession(
        answerFacts.owner(), answerFacts.sessionId())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    session.toDomain().assertCanAnswer(answerFacts.answer());
    List<AdaptiveAgentPlanEntity> planEntities = planRepository
        .findBySessionIdOrderByDimensionOrder(answerFacts.sessionId());
    InterviewPlan plan = toPlan(
        answerFacts.sessionId(), session.toDomain().maxTurns(), planEntities);
    InterviewWorkState before = workStatePersistenceService.get(answerFacts.sessionId());
    PlannedDimension dimension = plan.dimension(
        before.activeTarget().target().identity().order());
    AdaptiveAgentTurnEntity turn = answeredTurn(answerFacts);
    turn.recordAnswer(answerFacts.answer());
    InterviewWorkState updated = applyPatches(input.preparation().decisionPatches(), before);
    AdaptiveAgentAssessmentEntity assessment = saveAssessment(
        dimension, input.assessment());
    episodeFactPersistence.create(new EpisodePersistenceInput(
        session, turn, assessment, dimension, before, updated, null));
    savePreparedFacts(
        input,
        new SavedAnswerContext(session, new AnswerAssessment(turn, assessment))
    );
    actionIntentPersistenceService.plan(
        input.preparation().action().intent(),
        input.preparation().action().pendingPatch()
    );
    sessionRepository.flush();
    return plannedInterview(session, plan);
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
  public PlannedInterview recordDecision(AdaptiveDecisionPersistenceInput input) {
    String sessionId = input.sessionId();
    CandidateAnswer answer = input.answer();
    RespondAction proposedAction = input.proposedAction();
    List<ToolExecution> toolExecutions = input.toolExecutions();
    DimensionBrief dimensionBrief = input.dimensionBrief();
    AssessmentDecision assessmentDecision = input.assessmentDecision();
    List<ValidatedAssessmentEvidence> assessmentEvidences = input.assessmentEvidences();
    List<PracticeRecommendation> practiceRecommendations = input.practiceRecommendations();
    AdaptiveAgentSessionEntity sessionEntity = findOwnedSession(input.owner(), sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Agent 面试会话不存在"
        ));
    AdaptiveInterviewSession currentSession = sessionEntity.toDomain();
    currentSession.assertCanAnswer(answer);
    List<AdaptiveAgentPlanEntity> planEntities = planRepository
        .findBySessionIdOrderByDimensionOrder(sessionId);
    InterviewPlan plan = toPlan(sessionId, sessionEntity.toDomain().maxTurns(), planEntities);
    InterviewWorkState before = workStatePersistenceService.get(sessionId);
    PlannedDimension answeredDimension = plan.dimension(
        before.activeTarget().target().identity().order());
    SessionTransition transition = currentSession.apply(answer, proposedAction);
    AdaptiveAgentTurnEntity turnEntity = turnRepository
        .findBySessionIdAndTurnIndex(sessionId, answer.turnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));

    turnEntity.complete(answer, transition.appliedAction());
    sessionEntity.apply(transition.session());
    InterviewWorkState updatedState = before;
    for (WorkStatePatch patch : input.workStatePatches()) {
      updatedState = workStatePersistenceService.apply(patch);
    }
    sessionRepository.flush();

    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.save(
        new AdaptiveAgentAssessmentEntity(answeredDimension.order(), assessmentDecision)
    );
    List<AssessmentProbeGapEntity> savedProbeGaps = saveProbeGaps(
        assessment,
        assessmentDecision
    );
    episodeFactPersistence.create(new EpisodePersistenceInput(
        sessionEntity,
        turnEntity,
        assessment,
        answeredDimension,
        before,
        updatedState,
        null
    ));

    if (transition.appliedAction().type() == AgentResponseType.ASK) {
      int nextTurn = transition.session().currentTurn();
      TurnProvenance provenance = input.nextTurnProvenance().resolve(
          assessment.id(),
          probeGapIds(savedProbeGaps)
      );
      turnRepository.save(new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
          sessionId,
          nextTurn,
          updatedState.activeTarget().target().identity().order(),
          transition.appliedAction(),
          provenance
      )));
    }
    saveToolExecutions(sessionId, toolExecutions);
    if (dimensionBrief != null) {
      dimensionBriefRepository.save(new AdaptiveDimensionBriefEntity(dimensionBrief));
    }
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
    return plannedInterview(sessionEntity, plan);
  }

  private AdaptiveAgentTurnEntity answeredTurn(AdaptiveAnswerFacts facts) {
    return answeredTurn(facts.sessionId(), facts.answer().turnIndex());
  }

  private AdaptiveAgentTurnEntity answeredTurn(String sessionId, int turnIndex) {
    return turnRepository.findBySessionIdAndTurnIndex(sessionId, turnIndex)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
  }

  private InterviewWorkState applyPatches(
      List<WorkStatePatch> patches,
      InterviewWorkState initial
  ) {
    InterviewWorkState updated = initial;
    for (WorkStatePatch patch : patches) {
      updated = workStatePersistenceService.apply(patch);
    }
    return updated;
  }

  private AdaptiveAgentAssessmentEntity saveAssessment(
      PlannedDimension dimension,
      AdaptiveAssessmentFacts facts
  ) {
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository.save(
        new AdaptiveAgentAssessmentEntity(dimension.order(), facts.decision()));
    saveProbeGaps(assessment, facts.decision());
    return assessment;
  }

  private void savePreparedFacts(
      AdaptiveActionPreparationInput input,
      SavedAnswerContext context
  ) {
    AdaptiveMemoryFacts memory = input.preparation().memory();
    if (memory.dimensionBrief() != null) {
      dimensionBriefRepository.save(new AdaptiveDimensionBriefEntity(memory.dimensionBrief()));
    }
    saveAssessmentEvidence(
        input.assessment(), input.answer().answer(), context.saved().assessment());
    saveCodeFactEvidence(
        context.saved().assessment(),
        context.saved().turn()
    );
    practiceRecordRepository.saveAll(input.assessment().recommendations().stream()
        .map(recommendation -> new PracticeRecordEntity(context.session(), recommendation))
        .toList());
  }

  private void saveAssessmentEvidence(
      AdaptiveAssessmentFacts facts,
      CandidateAnswer answer,
      AdaptiveAgentAssessmentEntity assessment
  ) {
    evidenceRepository.saveAll(facts.evidences().stream()
        .map(evidence -> new AdaptiveAgentEvidenceEntity(
            assessment,
            facts.decision().sessionId(),
            answer.turnIndex(),
            evidence
        ))
        .toList());
  }

  private record SavedAnswerContext(
      AdaptiveAgentSessionEntity session,
      AnswerAssessment saved
  ) {}

  private record AnswerAssessment(
      AdaptiveAgentTurnEntity turn,
      AdaptiveAgentAssessmentEntity assessment
  ) {}


  private Optional<AdaptiveAgentSessionEntity> findOwnedSession(
      MemoryOwner owner,
      String sessionId
  ) {
    return owner.tenantId() == null
        ? sessionRepository.findByIdAndCandidateIdAndTenantIdIsNull(
            sessionId,
            owner.candidateId()
        )
        : sessionRepository.findByIdAndCandidateIdAndTenantId(
            sessionId,
            owner.candidateId(),
            owner.tenantId()
        );
  }

  private List<AssessmentProbeGapEntity> saveProbeGaps(
      AdaptiveAgentAssessmentEntity assessment,
      AssessmentDecision decision
  ) {
    List<AssessmentProbeGapEntity> gaps = new ArrayList<>();
    for (int index = 0; index < decision.probeGaps().size(); index++) {
      gaps.add(new AssessmentProbeGapEntity(
          assessment,
          index + 1,
          decision.probeGaps().get(index)
      ));
    }
    return probeGapRepository.saveAllAndFlush(gaps);
  }

  private Map<Integer, Long> probeGapIds(List<AssessmentProbeGapEntity> gaps) {
    return gaps.stream().collect(Collectors.toUnmodifiableMap(
        AssessmentProbeGapEntity::gapOrder,
        AssessmentProbeGapEntity::id
    ));
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
        workStatePersistenceService.find(sessionEntity.id()),
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
