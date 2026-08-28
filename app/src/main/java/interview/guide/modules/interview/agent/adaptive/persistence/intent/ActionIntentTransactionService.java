package interview.guide.modules.interview.agent.adaptive.persistence.intent;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentOutcome;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentToolCallEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentToolCallRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAskIntentCompletion;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptivePlannedAction;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionIntentTransactionService {

  private static final String TARGET_PREFIX = "target-";

  private final AdaptiveAgentSessionRepository sessionRepository;
  private final AdaptiveAgentTurnRepository turnRepository;
  private final AdaptiveAgentPlanRepository planRepository;
  private final AdaptiveAgentToolCallRepository toolCallRepository;
  private final AdaptiveAgentAssessmentRepository assessmentRepository;
  private final AssessmentProbeGapRepository probeGapRepository;
  private final WorkStatePersistenceService workStateService;
  private final ActionIntentPersistenceService intentService;
  private final QuestionExposurePersistence exposurePersistence;

  @Transactional
  public void initializePlan(String sessionId, InterviewPlan plan) {
    AdaptiveAgentSessionEntity session = session(sessionId);
    AdaptiveInterviewSession current = session.toDomain();
    session.apply(new AdaptiveInterviewSession(
        current.id(), current.runtimeVersion(), current.status(), current.currentTurn(),
        plan.maxTurns(), current.settings()));
    planRepository.saveAll(plan.dimensions().stream()
        .map(dimension -> new AdaptiveAgentPlanEntity(sessionId, dimension))
        .toList());
    workStateService.initializeReady(plan);
  }

  @Transactional
  public void planAction(
      String sessionId,
      List<WorkStatePatch> policyPatches,
      AdaptivePlannedAction action
  ) {
    session(sessionId);
    InterviewWorkState state = workStateService.get(sessionId);
    for (WorkStatePatch patch : policyPatches) {
      state = workStateService.apply(patch);
    }
    intentService.plan(action.intent(), action.pendingPatch());
  }

  @Transactional
  public void completeAsk(AdaptiveAskIntentCompletion completion) {
    ActionIntent intent = intentService.get(completion.intentId());
    AskActionPayload payload = (AskActionPayload) intent.payload();
    AdaptiveAgentSessionEntity session = session(completion.sessionId());
    TurnProvenance provenance = resolveProvenance(completion.sessionId(), payload);
    advanceSession(session, completion);
    AdaptiveAgentTurnEntity turn = turnRepository.saveAndFlush(
        new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        completion.sessionId(),
        payload.target().turnIndex(),
        targetOrder(completion.sessionId(), payload.target().targetId()),
        completion.action(),
        provenance
    )));
    exposurePersistence.save(session, turn, completion.publication());
    intentService.succeed(
        completion.intentId(),
        ActionIntentOutcome.succeeded(
            ActionResultType.QUESTION, "turn:" + payload.target().turnIndex())
    );
  }

  @Transactional
  public void completeTool(String sessionId, String intentId, ToolExecution execution) {
    toolCallRepository.save(new AdaptiveAgentToolCallEntity(sessionId, execution));
    intentService.succeed(
        intentId,
        ActionIntentOutcome.succeeded(ActionResultType.TOOL_RESULT, execution.invocationId())
    );
  }

  @Transactional(readOnly = true)
  public ToolExecution toolExecution(String invocationId) {
    return toolCallRepository.findByInvocationId(invocationId)
        .map(AdaptiveAgentToolCallEntity::toDomain)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工具执行结果不存在"));
  }

  @Transactional(readOnly = true)
  public Optional<CandidateAnswer> currentCandidateAnswer(String sessionId) {
    AdaptiveAgentSessionEntity session = session(sessionId);
    return turnRepository.findBySessionIdAndTurnIndex(
        sessionId, session.toDomain().currentTurn()).map(AdaptiveAgentTurnEntity::candidateAnswer);
  }

  private void advanceSession(
      AdaptiveAgentSessionEntity session,
      AdaptiveAskIntentCompletion completion
  ) {
    if (session.status() == AdaptiveSessionStatus.CREATED) {
      session.apply(session.toDomain().start());
      return;
    }
    turnRepository.findBySessionIdAndTurnIndex(
            completion.sessionId(), session.toDomain().currentTurn())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "当前回答轮次不存在"))
        .recordResponse(completion.action());
    session.apply(session.toDomain().advanceAfterAnswer());
  }

  private TurnProvenance resolveProvenance(String sessionId, AskActionPayload payload) {
    if (payload.provenance().parentTurnIndex() == null) {
      return payload.provenance().resolve(0, Map.of());
    }
    AdaptiveAgentAssessmentEntity assessment = assessmentRepository
        .findBySessionIdAndTurnIndex(sessionId, payload.provenance().parentTurnIndex())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问 Assessment 不存在"));
    return payload.provenance().resolve(
        assessment.id(),
        probeGapIds(probeGapRepository.findByAssessmentIdOrderByGapOrderAscIdAsc(
            assessment.id()))
    );
  }

  private Map<Integer, Long> probeGapIds(List<AssessmentProbeGapEntity> gaps) {
    return gaps.stream().collect(Collectors.toMap(
        AssessmentProbeGapEntity::gapOrder,
        AssessmentProbeGapEntity::id
    ));
  }

  private int targetOrder(String sessionId, String targetId) {
    int order = Integer.parseInt(targetId.substring(TARGET_PREFIX.length()));
    boolean exists = planRepository.findBySessionIdOrderByDimensionOrder(sessionId).stream()
        .anyMatch(plan -> plan.dimensionOrder() == order);
    if (!exists) {
      throw new IllegalStateException("Intent 目标不在计划中");
    }
    return order;
  }

  private AdaptiveAgentSessionEntity session(String sessionId) {
    return sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "Agent 面试会话不存在"));
  }
}
