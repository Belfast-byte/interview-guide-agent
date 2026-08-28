package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerWorkView;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionType;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkEvidenceRef;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkIssueStatus;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkPhase;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticePlanningMemory;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemorySession;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.intent.ActionIntentTransactionService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveActionPreparation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveActionPreparationInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAnswerFacts;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAssessmentFacts;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveMemoryFacts;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptivePlannedAction;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveDecisionPersistenceInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActResult;
import interview.guide.modules.interview.agent.adaptive.tool.SandboxSubmitTool;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateChatProvider;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 自适应面试应用服务，是面试流程的总编排入口，负责创建会话、提交回答和生成报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewApplicationService {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final ActionIntentTransactionService intentTransactions;
  private final PersistentActionCoordinator actionCoordinator;
  private final AdaptiveInterviewRequestFactory requestFactory;
  private final AdaptiveAgentTelemetry telemetry;
  private final PlanningAgent planningAgent;
  private final ContextAssembler contextAssembler;
  private final DimensionBriefService dimensionBriefService;
  private final PlanningTaxonomy planningTaxonomy;
  private final CandidateClaimExtractionService candidateClaimExtractionService;
  private final PracticeMemoryService practiceMemoryService;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator assessmentEvidenceValidator;
  private final PracticeRecommendationService practiceRecommendationService;
  private final AlgorithmInterviewTelemetry algorithmTelemetry;
  private final InterviewSkillService skillService;
  private final CandidateLlmProviderService candidateProviderService;
  private final AdaptiveInterviewCreationTaskRunner creationExecutor;
  private final AdaptiveInterviewAnswerExecutor answerExecutor;

  public PlannedInterview createForCandidate(CandidateInterviewCreationCommand command) {
    return create(resolveCandidateInput(command));
  }

  public PlannedInterview createForCandidateStreaming(
      CandidateInterviewCreationCommand command,
      InterviewCreationEventSink sink
  ) {
    return createStreaming(resolveCandidateInput(command), sink);
  }

  private InterviewCreationInput resolveCandidateInput(
      CandidateInterviewCreationCommand command
  ) {
    CandidateChatProvider provider = candidateProviderService.resolveChatProvider(
        command.candidateId(),
        command.requestedProviderId()
    );
    return new InterviewCreationInput(
        null,
        command.candidateId().toString(),
        command.jd(),
        command.resume(),
        provider.id(),
        provider.displayName(),
        provider.model(),
        command.settings()
    );
  }

  /**
   * 创建指定租户下的自适应面试，用于多租户隔离场景。
   *
   * @param tenantId 租户 ID
   * @param candidateId 候选人 ID
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param llmProvider 使用的 LLM 供应商
   * @return CREATED 骨架会话
   */
  public PlannedInterview createForTenant(TenantInterviewCreationCommand command) {
    return create(new InterviewCreationInput(
        command.tenantId(),
        command.candidateId(),
        command.jd(),
        command.resume(),
        command.llmProvider(),
        null,
        null,
        command.settings()
    ));
  }

  private PlannedInterview create(InterviewCreationInput input) {
    String sessionId = UUID.randomUUID().toString();
    PlannedInterview skeleton = persistenceService.createSkeleton(
        input.toSessionCreation(sessionId)
    );
    try {
      submitCreation(sessionId, input, InterviewCreationEventSink.noop());
    } catch (RejectedExecutionException e) {
      persistenceService.failCreation(sessionId, "创建队列已满，请稍后重试");
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "自适应面试创建任务提交失败", e);
    }
    return skeleton;
  }

  private PlannedInterview createStreaming(
      InterviewCreationInput input,
      InterviewCreationEventSink sink
  ) {
    String sessionId = UUID.randomUUID().toString();
    PlannedInterview skeleton = persistenceService.createSkeleton(
        input.toSessionCreation(sessionId)
    );
    sink.onCreated(skeleton);
    try {
      submitCreation(sessionId, input, sink);
    } catch (RejectedExecutionException e) {
      String message = "创建队列已满，请稍后重试";
      persistenceService.failCreation(sessionId, message);
      sink.onFailed(message);
    }
    return skeleton;
  }

  private void submitCreation(
      String sessionId,
      InterviewCreationInput input,
      InterviewCreationEventSink sink
  ) {
    creationExecutor.submit(() -> {
      try {
        sink.onCompleted(generateFirstTurn(sessionId, input, sink.deltaSink()));
      } catch (Exception e) {
        String message = readableFailure(e);
        log.error("自适应面试创建失败: sessionId={}", sessionId, e);
        persistenceService.failCreation(sessionId, message);
        sink.onFailed(message);
      }
    });
  }

  /**
   * 创建链路的后半段：规划面试计划并生成首轮决策（LLM 调用全部在事务外）。
   */
  private PlannedInterview generateFirstTurn(
      String sessionId,
      InterviewCreationInput input,
      Consumer<String> deltaSink
  ) {
    InterviewPlan plan = decidePlan(sessionId, input);
    PlannedDimension firstDimension = plan.dimension(0);
    intentTransactions.initializePlan(sessionId, plan);
    PlannedInterview initialized = persistenceService.get(sessionId);
    WorkStatePolicyPlanner.PolicyDecision policy = WorkStatePolicyPlanner.decide(
        initialized.workState(), "initial");
    requireAction(policy.action().type(), NextActionType.ASK);
    AdaptivePlannedAction action = ActionIntentPlanFactory.ask(
        policy.state(),
        new ActionTarget(policy.state().activeTargetId(), null, 1),
        new AskActionContext(NextTurnProvenanceDraft.planned(), null)
    );
    intentTransactions.planAction(sessionId, policy.patches(), action);
    ReActRequest request = requestFactory.create(new InterviewerDecisionInput(
        sessionId,
        input.llmProviderId(),
        input.jd(),
        input.resume(),
        plan.maxTurns(),
        firstDimension,
        List.of(),
        null,
        InterviewerWorkView.from(policy.state(), null),
        new PracticeMemorySession(
            sessionId,
            input.settings().mode()
        )
    ));
    return actionCoordinator.executeInitialAsk(action, request, deltaSink);
  }

  private InterviewPlan decidePlan(String sessionId, InterviewCreationInput input) {
    PlanProposal proposal = planningAgent.propose(
        new PlanningRequest(sessionId, contextAssembler.planner(new PlannerContext(
            input.jd(),
            input.resume(),
            input.settings().mode(),
            input.settings().candidateLevel(),
            input.settings().practiceScope().topics(),
            planningTaxonomy.catalog()
        )), practiceMemory(input)),
        input.llmProviderId()
    );
    InterviewPlan plan;
    try {
      plan = InterviewPlan.decide(sessionId, proposal, input.settings());
      planningTaxonomy.validate(plan);
    } catch (BusinessException e) {
      telemetry.planRejected(sessionId, e.getCode());
      throw e;
    }
    return plan;
  }

  private PracticePlanningMemory practiceMemory(InterviewCreationInput input) {
    if (input.settings().mode() != SessionMode.PRACTICE) {
      return null;
    }
    return practiceMemoryService.planning(
        new MemoryOwner(input.tenantId(), input.candidateId()),
        input.settings().practiceScope()
    );
  }

  private record InterviewCreationInput(
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProviderId,
      String llmProviderNameSnapshot,
      String llmModelSnapshot,
      InterviewSessionSettings settings
  ) {

    AdaptiveSessionCreation toSessionCreation(String sessionId) {
      return new AdaptiveSessionCreation(
          tenantId,
          sessionId,
          candidateId,
          jd,
          resume,
          llmProviderId,
          llmProviderNameSnapshot,
          llmModelSnapshot,
          settings
      );
    }
  }

  private String readableFailure(Exception e) {
    String message = e instanceof BusinessException businessException
        ? businessException.getMessage()
        : e.getMessage();
    return message == null || message.isBlank() ? "创建链路未知异常" : message;
  }

  /**
   * 提交候选人回答：执行深度评估、持久化轮次，并让面试官 Agent 决定下一轮动作。
   *
   * @param sessionId 会话 ID
   * @param answer 候选人回答
   * @return 推进后的面试状态
   */
  public PlannedInterview submitAnswer(String sessionId, CandidateAnswer answer) {
    return submitAnswer(new AnswerSubmissionInput(
        null,
        sessionId,
        answer,
        AnswerEventSink.noop()
    ));
  }

  /**
   * 流式提交候选人回答：业务顺序与同步路径完全一致，
   * 仅多了阶段切换与决策增量文本事件；末轮决策是代码 FINISH，无增量内容。
   *
   * @param candidateId 候选人 ID
   * @param sessionId 会话 ID
   * @param answer 候选人回答
   * @param sink 事件回调
   * @return 推进后的面试状态
   */
  public PlannedInterview submitAnswerStreaming(
      String candidateId,
      String sessionId,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    return submitAnswer(new AnswerSubmissionInput(null, sessionId, answer, sink));
  }

  private PlannedInterview submitAnswer(AnswerSubmissionInput input) {
    String sessionId = input.sessionId();
    CandidateAnswer answer = input.answer();
    PlannedInterview interview = input.tenantId() == null
        ? persistenceService.get(sessionId)
        : persistenceService.getForTenant(input.tenantId(), sessionId);
    AdaptiveInterviewHistory history = interview.history();
    MemoryOwner owner = new MemoryOwner(input.tenantId(), history.candidateId());
    history.session().assertCanAnswer(answer);
    InterviewWorkState workState = interview.workState();
    if (workState.phase() == WorkPhase.ACTION_PENDING) {
      return actionCoordinator.resume(interview, answer, input.sink());
    }
    if (workState.phase() == WorkPhase.READY_TO_DECIDE) {
      return continuePreparedAnswer(interview, answer, input.sink(), null);
    }
    PlannedDimension currentDimension = interview.plan().dimension(
        workState.activeTarget().target().identity().order());
    algorithmTelemetry.interviewTurnSubmitted(sessionId);
    input.sink().onStage(AnswerEventSink.AnswerStage.ASSESSING);
    AssessmentResult assessed = assessAnswer(interview, currentDimension, answer);
    if (answer.codeSubmission() != null) {
      return prepareSandboxAction(
          input, interview, owner, currentDimension, assessed, workState);
    }
    AssessmentWorkStatePlanner.PreparedWorkDecision prepared = AssessmentWorkStatePlanner.prepare(
        workState, assessed.decision(), assessed.evidences());
    boolean targetEnded = targetEnded(prepared, workState.activeTargetId());
    boolean sessionEnded = prepared.action().type() == NextActionType.FINISH;
    MemoryArtifacts artifacts = memoryArtifacts(new MemoryArtifactInput(
        interview, currentDimension, answer, assessed.decision(), targetEnded, sessionEnded));
    Optional<DepthLevel> previousDepth = previousDepth(
        sessionId, currentDimension, workState);
    if (prepared.action().type() != NextActionType.FINISH) {
      return prepareExternalAction(new ExternalActionInput(
          input,
          interview,
          owner,
          currentDimension,
          assessed,
          prepared,
          artifacts,
          previousDepth
      ));
    }
    NextDecision nextDecision = finishDecision();
    return persistDecision(new DecisionPersistence(
        sessionId, owner, answer, currentDimension, assessed, prepared, artifacts,
        nextDecision, previousDepth));
  }

  private AssessmentResult assessAnswer(
      PlannedInterview interview,
      PlannedDimension dimension,
      CandidateAnswer answer
  ) {
    AdaptiveInterviewHistory history = interview.history();
    AssessmentDecision decision = assessmentAgent.assess(
        new AssessmentRequest(
            history.session().id(),
            answer.turnIndex(),
            AssessmentContext.currentAnswer(
                dimension.dimension(),
                dimension.focus(),
                history.turns().getLast().question(),
                answer.content()
            ),
            skillService.buildEvaluationReferenceSection(dimension.suggestedSkill())
        ),
        history.llmProvider()
    );
    List<ValidatedAssessmentEvidence> evidences = assessmentEvidenceValidator.validate(
        history.session().id(),
        answer.turnIndex(),
        answer.content(),
        decision.evidenceQuotes().stream().map(AssessmentEvidenceCandidate::quote).toList()
    );
    return new AssessmentResult(decision, evidences);
  }

  private MemoryArtifacts memoryArtifacts(MemoryArtifactInput input) {
    PlannedInterview interview = input.interview();
    PlannedDimension dimension = input.dimension();
    CandidateAnswer answer = input.answer();
    AdaptiveInterviewHistory history = interview.history();
    DimensionBrief dimensionBrief = null;
    List<CandidateClaim> candidateClaims = List.of();
    if (input.targetEnded() && input.sessionEnded()) {
      dimensionBrief = dimensionBriefService.summarize(
          history.session().id(),
          dimension,
          history.turns(),
          answer,
          history.llmProvider()
      );
      candidateClaims = candidateClaimExtractionService.extract(
          history.session().id(),
          dimension,
          history.turns(),
          answer,
          planningTaxonomy.catalog(),
          history.llmProvider()
      );
    } else if (input.targetEnded()) {
      answerExecutor.execute(() -> generateDimensionMemorySafely(
          history.session().id(),
          dimension,
          history.turns(),
          answer,
          history.llmProvider()
      ));
    }
    List<PracticeRecommendation> recommendations = input.sessionEnded()
        ? practiceRecommendationService.recommend(
            history.session().id(),
            dimension,
            input.assessment()
        )
        : List.of();
    return new MemoryArtifacts(dimensionBrief, candidateClaims, recommendations);
  }

  private PlannedInterview persistDecision(DecisionPersistence input) {
    String sessionId = input.sessionId();
    try {
      PlannedInterview updated = persistenceService.recordDecision(persistenceInput(input));
      recordAssessmentTelemetry(
          input.dimension(), input.assessed(), input.previousDepth());
      return updated;
    } catch (OptimisticLockingFailureException e) {
      telemetry.stateConflict(sessionId, input.answer().turnIndex());
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "面试会话已被其他请求推进，请刷新后重试",
          e
      );
    }
  }

  private AdaptiveDecisionPersistenceInput persistenceInput(DecisionPersistence input) {
    CandidateAnswer answer = input.answer();
    ReActResult decision = input.nextDecision().decision();
    MemoryArtifacts artifacts = input.artifacts();
    return new AdaptiveDecisionPersistenceInput(
        input.owner(),
        input.sessionId(),
        answer,
        decision.response(),
        decision.toolExecutions(),
        artifacts.dimensionBrief(),
        artifacts.candidateClaims(),
        input.assessed().decision(),
        input.assessed().evidences(),
        artifacts.practiceRecommendations(),
        input.nextDecision().provenance(),
        input.prepared().finalPatches(nextTurnIndex(answer, decision))
    );
  }

  private NextDecision finishDecision() {
    return new NextDecision(ReActResult.withoutTools(RespondAction.finish(
        "面试已覆盖全部能力目标。", "工作状态中的能力目标均已终态")),
        NextTurnProvenanceDraft.planned());
  }

  private PlannedInterview prepareExternalAction(ExternalActionInput input) {
    input.input().sink().onStage(AnswerEventSink.AnswerStage.GENERATING);
    ReActRequest request = requestFactory.action(
        input.interview(),
        input.input().answer(),
        InterviewerWorkView.from(
            input.prepared().projectedState(), input.prepared().action().issueId())
    );
    AdaptivePlannedAction action = plannedAction(
        input.prepared(), input.input().answer(), request, null);
    persistenceService.prepareAction(preparationInput(input, action));
    recordPreparedAssessment(input);
    return executePreparedAction(
        action,
        request,
        input.interview(),
        input.input().answer(),
        input.input().sink()
    );
  }

  private AdaptiveActionPreparationInput preparationInput(
      ExternalActionInput input,
      AdaptivePlannedAction action
  ) {
    MemoryArtifacts artifacts = input.artifacts();
    return new AdaptiveActionPreparationInput(
        new AdaptiveAnswerFacts(
            input.owner(), input.input().sessionId(), input.input().answer()),
        new AdaptiveAssessmentFacts(
            input.assessed().decision(),
            input.assessed().evidences(),
            artifacts.practiceRecommendations()
        ),
        new AdaptiveActionPreparation(
            new AdaptiveMemoryFacts(
                artifacts.dimensionBrief(), artifacts.candidateClaims()),
            input.prepared().patches(),
            action
        )
    );
  }

  private AdaptivePlannedAction plannedAction(
      AssessmentWorkStatePlanner.PreparedWorkDecision prepared,
      CandidateAnswer answer,
      ReActRequest request,
      ToolResultEvent toolResult
  ) {
    ActionTarget target = new ActionTarget(
        prepared.projectedState().activeTargetId(),
        prepared.action().issueId(),
        prepared.action().type() == NextActionType.ASK
            ? answer.turnIndex() + 1
            : answer.turnIndex()
    );
    if (prepared.action().type() == NextActionType.ASK) {
      return ActionIntentPlanFactory.ask(
          prepared.projectedState(),
          target,
          new AskActionContext(prepared.provenance(), toolResult)
      );
    }
    requireAction(prepared.action().type(), NextActionType.CALL_TOOL);
    return ActionIntentPlanFactory.tool(
        prepared.projectedState(), target, actionCoordinator.proposeTool(request));
  }

  private PlannedInterview executePreparedAction(
      AdaptivePlannedAction action,
      ReActRequest request,
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {
    return actionCoordinator.execute(new PersistentActionCoordinator.PreparedActionExecution(
        action, request, interview, answer, sink));
  }

  private PlannedInterview prepareSandboxAction(
      AnswerSubmissionInput input,
      PlannedInterview interview,
      MemoryOwner owner,
      PlannedDimension dimension,
      AssessmentResult assessed,
      InterviewWorkState state
  ) {
    AssessmentWorkStatePlanner.AssessmentProjection projection =
        AssessmentWorkStatePlanner.assessOnly(
            state, assessed.decision(), assessed.evidences());
    ToolCallAction sandbox = sandboxProposal(input.answer());
    AdaptivePlannedAction action = ActionIntentPlanFactory.tool(
        projection.state(),
        new ActionTarget(state.activeTargetId(), null, input.answer().turnIndex()),
        sandbox
    );
    MemoryArtifacts artifacts = memoryArtifacts(new MemoryArtifactInput(
        interview, dimension, input.answer(), assessed.decision(), false, false));
    persistenceService.prepareAction(new AdaptiveActionPreparationInput(
        new AdaptiveAnswerFacts(owner, input.sessionId(), input.answer()),
        new AdaptiveAssessmentFacts(
            assessed.decision(), assessed.evidences(), artifacts.practiceRecommendations()),
        new AdaptiveActionPreparation(
            new AdaptiveMemoryFacts(
                artifacts.dimensionBrief(), artifacts.candidateClaims()),
            projection.patches(),
            action
        )
    ));
    recordAssessmentTelemetry(
        dimension, assessed, previousDepth(input.sessionId(), dimension, state));
    ReActRequest request = requestFactory.action(
        interview,
        input.answer(),
        InterviewerWorkView.from(projection.state(), null)
    );
    return executePreparedAction(action, request, interview, input.answer(), input.sink());
  }

  private ToolCallAction sandboxProposal(CandidateAnswer answer) {
    var submission = answer.codeSubmission();
    String targetName = submission.patch() ? "scenarioId" : "problemId";
    String targetId = submission.patch() ? submission.scenarioId() : submission.problemId();
    return new ToolCallAction(
        SandboxSubmitTool.NAME,
        Map.of(targetName, targetId, "runMode", submission.runMode()),
        "提交候选人当前代码进行异步判题"
    );
  }

  private PlannedInterview continuePreparedAnswer(
      PlannedInterview interview,
      CandidateAnswer answer,
      AnswerEventSink sink,
      ToolResultEvent toolResult
  ) {
    return actionCoordinator.continueAnswer(new PersistentActionCoordinator.ContinuationInput(
        interview, answer, sink, toolResult));
  }

  private void recordPreparedAssessment(ExternalActionInput input) {
    recordAssessmentTelemetry(
        input.dimension(), input.assessed(), input.previousDepth());
  }

  private void requireAction(NextActionType actual, NextActionType expected) {
    if (actual != expected) {
      throw new IllegalStateException("WorkState 策略动作与执行分支不一致");
    }
  }

  private boolean targetEnded(
      AssessmentWorkStatePlanner.PreparedWorkDecision prepared,
      String targetId
  ) {
    TargetWorkStatus status = prepared.projectedState().targets().stream()
        .filter(target -> target.targetId().equals(targetId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("WorkState 目标不存在"))
        .status();
    return status == TargetWorkStatus.COMPLETED || status == TargetWorkStatus.EXHAUSTED;
  }

  private Optional<DepthLevel> previousDepth(
      String sessionId,
      PlannedDimension dimension,
      InterviewWorkState state
  ) {
    int consumed = dimension.allocatedTurns() - state.activeTarget().remainingBudget().turns();
    return consumed <= 1
        ? Optional.empty()
        : Optional.of(persistenceService.latestAssessmentDepth(sessionId, dimension.order()));
  }

  private Integer nextTurnIndex(CandidateAnswer answer, ReActResult decision) {
    return decision.response().type() == AgentResponseType.ASK
        ? answer.turnIndex() + 1
        : null;
  }

  private void recordAssessmentTelemetry(
      PlannedDimension dimension,
      AssessmentResult assessed,
      Optional<DepthLevel> previousDepth
  ) {
    telemetry.assessmentRecorded(
        dimension.dimension(),
        assessed.decision().depthLevel(),
        assessed.evidences().size()
    );
    previousDepth.ifPresent(depth -> telemetry.followUpAssessed(
        dimension.dimension(), depth, assessed.decision().depthLevel()));
  }

  public PlannedInterview submitAnswerForCandidate(
      String candidateId,
      String sessionId,
      CandidateAnswer answer
  ) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    return submitAnswer(sessionId, answer);
  }

  public PlannedInterview submitAnswerForTenant(
      String tenantId,
      String sessionId,
      CandidateAnswer answer
  ) {
    return submitAnswer(new AnswerSubmissionInput(
        tenantId,
        sessionId,
        answer,
        AnswerEventSink.noop()
    ));
  }

  private record AnswerSubmissionInput(
      String tenantId,
      String sessionId,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {}

  /**
   * 获取指定自适应面试的当前状态。
   *
   * @param sessionId 会话 ID
   * @return 面试聚合
   */
  public PlannedInterview get(String sessionId) {
    return persistenceService.get(sessionId);
  }

  public PlannedInterview getForCandidate(String candidateId, String sessionId) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    return get(sessionId);
  }

  public void requireCandidateSession(String candidateId, String sessionId) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
  }

  /**
   * 获取指定租户下的面试状态。
   *
   * @param tenantId 租户 ID
   * @param sessionId 会话 ID
   * @return 面试聚合
   */
  public PlannedInterview getForTenant(String tenantId, String sessionId) {
    return persistenceService.getForTenant(tenantId, sessionId);
  }

  public PlannedInterview retryActionIntentForCandidate(
      String candidateId,
      String sessionId,
      String failedIntentId
  ) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    actionCoordinator.retry(sessionId, failedIntentId);
    return persistenceService.get(sessionId);
  }

  /**
   * 预留异步工具结果事件：任何 LLM 重评或追问生成之前先做幂等去重。
   *
   * @param sessionId 会话 ID
   * @param event 工具结果事件
   * @return true 表示预留成功；false 表示事件已存在或会话尚在创建期
   */
  public boolean reserveToolResultEvent(String sessionId, ToolResultEvent event) {
    try {
      return persistenceService.reserveToolResultEvent(sessionId, event);
    } catch (DataIntegrityViolationException e) {
      return false;
    }
  }

  /**
   * 处理已预留的异步工具结果事件：让面试官生成追问并完成事件。
   * 调用方必须先经 {@link #reserveToolResultEvent} 去重成功。
   *
   * @param sessionId 会话 ID
   * @param event 工具结果事件
   * @return 生成的追问响应；事件处理失败时抛出，预留由本方法回滚
   */
  public Optional<RespondAction> handleToolResult(
      String sessionId,
    ToolResultEvent event
  ) {
    PlannedInterview interview = persistenceService.get(sessionId);
    try {
      persistenceService.completeToolResultEvent(
          sessionId,
          event,
          toolResultPatch(interview.workState(), event)
      );
      return Optional.empty();
    } catch (Exception e) {
      persistenceService.discardToolResultReservation(event);
      throw e;
    }
  }

  /**
   * 回滚工具结果事件预留，供补偿调度器重新投递。
   *
   * @param event 工具结果事件
   */
  public void discardToolResultReservation(ToolResultEvent event) {
    persistenceService.discardToolResultReservation(event);
  }

  private WorkStatePatch toolResultPatch(
      InterviewWorkState state,
      ToolResultEvent event
  ) {
    List<WorkStateOperation> operations = new java.util.ArrayList<>();
    operations.add(new WorkStateOperation.AddEvidenceRef(new WorkEvidenceRef(
        state.activeTargetId(), event.toolName(), event.resultId(), event.summary())));
    state.activeOpenIssues().stream()
        .filter(issue -> issue.evidenceMethod() == CapabilityTarget.EvidenceMethod.TOOL_FACT)
        .findFirst()
        .ifPresent(issue -> operations.add(new WorkStateOperation.CloseIssue(
            issue.issueId(), WorkIssueStatus.RESOLVED, "工具事实已经返回")));
    return new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.TOOL_RESULT,
        event.toolName() + ":" + event.resultId(),
        operations
    );
  }

  /**
   * 查询指定会话中由工具结果触发的待处理/已完成追问列表。
   *
   * @param sessionId 会话 ID
   * @return 工具结果追问列表
   */
  public List<ToolResultFollowUp> toolResultFollowUps(String sessionId) {
    return persistenceService.toolResultFollowUps(sessionId);
  }

  public List<ToolResultFollowUp> toolResultFollowUpsForCandidate(
      String candidateId,
      String sessionId
  ) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    return toolResultFollowUps(sessionId);
  }

  /**
   * 维度记忆（小结 + 声明）异步生成：两个 LLM 调用并行，失败只记日志，
   * 不影响面试主流程；落库晚于当轮决策，从下一轮决策起可见。
   */
  private void generateDimensionMemorySafely(
      String sessionId,
      PlannedDimension dimension,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer answer,
      String llmProvider
  ) {
    try {
      CompletableFuture<DimensionBrief> briefFuture = CompletableFuture.supplyAsync(
          () -> dimensionBriefService.summarize(sessionId, dimension, turns, answer, llmProvider),
          answerExecutor
      );
      CompletableFuture<List<CandidateClaim>> claimsFuture = CompletableFuture.supplyAsync(
          () -> candidateClaimExtractionService.extract(
              sessionId,
              dimension,
              turns,
              answer,
              planningTaxonomy.catalog(),
              llmProvider
          ),
          answerExecutor
      );
      persistenceService.saveDimensionMemory(sessionId, briefFuture.join(), claimsFuture.join());
    } catch (Exception e) {
      log.warn(
          "维度记忆异步生成失败，不影响面试主流程: sessionId={}, dimension={}, error={}",
          sessionId,
          dimension.dimension(),
          e.getMessage()
      );
    }
  }

  private record NextDecision(
      ReActResult decision,
      NextTurnProvenanceDraft provenance
  ) {}

  private record AssessmentResult(
      AssessmentDecision decision,
      List<ValidatedAssessmentEvidence> evidences
  ) {}

  private record MemoryArtifacts(
      DimensionBrief dimensionBrief,
      List<CandidateClaim> candidateClaims,
      List<PracticeRecommendation> practiceRecommendations
  ) {}

  private record MemoryArtifactInput(
      PlannedInterview interview,
      PlannedDimension dimension,
      CandidateAnswer answer,
      AssessmentDecision assessment,
      boolean targetEnded,
      boolean sessionEnded
  ) {}

  private record DecisionPersistence(
      String sessionId,
      MemoryOwner owner,
      CandidateAnswer answer,
      PlannedDimension dimension,
      AssessmentResult assessed,
      AssessmentWorkStatePlanner.PreparedWorkDecision prepared,
      MemoryArtifacts artifacts,
      NextDecision nextDecision,
      Optional<DepthLevel> previousDepth
  ) {}

  private record ExternalActionInput(
      AnswerSubmissionInput input,
      PlannedInterview interview,
      MemoryOwner owner,
      PlannedDimension dimension,
      AssessmentResult assessed,
      AssessmentWorkStatePlanner.PreparedWorkDecision prepared,
      MemoryArtifacts artifacts,
      Optional<DepthLevel> previousDepth
  ) {}
}
