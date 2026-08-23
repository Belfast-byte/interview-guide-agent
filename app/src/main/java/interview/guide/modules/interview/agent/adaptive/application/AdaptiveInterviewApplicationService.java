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
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.InterviewerContextInput;
import interview.guide.modules.interview.agent.adaptive.memory.ToolResultContextInput;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryInput;
import interview.guide.modules.interview.agent.adaptive.memory.working.NextQuestionWorkingMemoryInput;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemorySelection;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveDecisionPersistenceInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActResult;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import interview.guide.modules.interview.agent.adaptive.tool.SandboxSubmitTool;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateChatProvider;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.List;
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
  private final BoundedReActRuntime runtime;
  private final AgentRoleRegistry roleRegistry;
  private final AdaptiveAgentTelemetry telemetry;
  private final PlanningAgent planningAgent;
  private final ContextAssembler contextAssembler;
  private final WorkingMemoryFactSource workingMemoryFactSource;
  private final DimensionBriefService dimensionBriefService;
  private final CandidateMemoryService candidateMemoryService;
  private final EpisodePromptMemoryService episodePromptMemoryService;
  private final PlanningTaxonomy planningTaxonomy;
  private final CandidateClaimExtractionService candidateClaimExtractionService;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator assessmentEvidenceValidator;
  private final PracticeRecommendationService practiceRecommendationService;
  private final AlgorithmAssessmentEvidenceService algorithmAssessmentEvidenceService;
  private final AlgorithmInterviewTelemetry algorithmTelemetry;
  private final CodeAnalysisInterviewContextService codeAnalysisContextService;
  private final InterviewSkillService skillService;
  private final CandidateLlmProviderService candidateProviderService;
  private final AdaptiveInterviewCreationTaskRunner creationExecutor;
  private final AdaptiveInterviewAnswerExecutor answerExecutor;

  /**
   * 创建一次非租户维度的自适应面试：落 CREATED 骨架后立即返回，规划与首题在后台生成。
   *
   * @param candidateId 候选人 ID
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param llmProvider 使用的 LLM 供应商
   * @return CREATED 骨架会话
   */
  PlannedInterview create(
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    return create(new InterviewCreationInput(
        null,
        candidateId,
        jd,
        resume,
        llmProvider,
        null,
        null
    ));
  }

  public PlannedInterview createForCandidate(
      UUID candidateId,
      String jd,
      String resume,
      String requestedProviderId
  ) {
    return create(resolveCandidateInput(new CandidateInterviewCreationCommand(
        candidateId,
        jd,
        resume,
        requestedProviderId
    )));
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
        provider.model()
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
  public PlannedInterview createForTenant(
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    return create(new InterviewCreationInput(
        tenantId,
        candidateId,
        jd,
        resume,
        llmProvider,
        null,
        null
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
    PlannedDimension firstDimension = plan.dimensionForTurn(1);
    ReActResult firstDecision = runDecision(
        request(new InterviewerDecisionInput(
            sessionId,
            input.llmProviderId(),
            input.jd(),
            input.resume(),
            plan.maxTurns(),
            firstDimension,
            List.of(),
            null,
            firstWorkingMemory(sessionId, firstDimension)
        )),
        deltaSink
    );
    return persistenceService.completeCreation(
        sessionId,
        plan,
        firstDecision.response(),
        firstDecision.toolExecutions()
    );
  }

  private InterviewPlan decidePlan(String sessionId, InterviewCreationInput input) {
    PlanProposal proposal = planningAgent.propose(
        new PlanningRequest(sessionId, contextAssembler.planner(
            input.jd(),
            input.resume(),
            input.tenantId() == null
                ? candidateMemoryService.coveredTopics(input.candidateId())
                : candidateMemoryService.coveredTopics(input.tenantId(), input.candidateId()),
            input.tenantId() == null
                ? candidateMemoryService.unverifiedClaims(input.candidateId())
                : candidateMemoryService.unverifiedClaims(input.tenantId(), input.candidateId()),
            planningTaxonomy.catalog()
        )),
        input.llmProviderId()
    );
    InterviewPlan plan;
    try {
      plan = InterviewPlan.decide(sessionId, proposal);
      planningTaxonomy.validate(plan);
    } catch (BusinessException e) {
      telemetry.planRejected(sessionId, e.getCode());
      throw e;
    }
    return plan;
  }

  private WorkingMemorySnapshot firstWorkingMemory(
      String sessionId,
      PlannedDimension dimension
  ) {
    return contextAssembler.workingMemory(new WorkingMemoryInput(
        sessionId,
        1,
        new TopicKey(dimension.suggestedSkill(), dimension.focusId()),
        null,
        TurnTriggerType.PLANNED,
        List.of(),
        List.of()
    ));
  }

  private record InterviewCreationInput(
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProviderId,
      String llmProviderNameSnapshot,
      String llmModelSnapshot
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
          llmModelSnapshot
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
    return submitAnswer(sessionId, answer, AnswerEventSink.noop());
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
    return submitAnswer(sessionId, answer, sink);
  }

  private PlannedInterview submitAnswer(
      String sessionId,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {
    PlannedInterview interview = persistenceService.get(sessionId);
    AdaptiveInterviewHistory history = interview.history();
    history.session().assertCanAnswer(answer);
    PlannedDimension currentDimension = interview.plan().dimensionForTurn(answer.turnIndex());
    algorithmTelemetry.interviewTurnSubmitted(sessionId);
    sink.onStage(AnswerEventSink.AnswerStage.ASSESSING);
    AssessmentDecision assessment = assessmentAgent.assess(
        new AssessmentRequest(
            sessionId,
            answer.turnIndex(),
            AssessmentContext.currentAnswer(
                currentDimension.dimension(),
                currentDimension.focus(),
                history.turns().getLast().question(),
                answer.content()
            ),
            skillService.buildEvaluationReferenceSection(
                currentDimension.suggestedSkill()
            )
        ),
        history.llmProvider()
    );
    List<ValidatedAssessmentEvidence> assessmentEvidences =
        assessmentEvidenceValidator.validate(
            sessionId,
            answer.turnIndex(),
            answer.content(),
            assessment.evidenceQuotes().stream()
                .map(AssessmentEvidenceCandidate::quote)
                .toList()
        );
    boolean naturallyCompletes = completesDimension(currentDimension);
    InterviewPlan planAfterEarlyCompletion = assessment.recommendsEarlyCompletion()
        ? interview.plan().completeDimensionEarly(answer.turnIndex())
        : interview.plan();
    // 末维度或末轮的提前完成是空操作（计划原样返回），按未提前完成处理，
    // 避免重复写 DimensionBrief 和 claims
    boolean earlyCompletion = !naturallyCompletes
        && planAfterEarlyCompletion != interview.plan();
    InterviewPlan planForNextTurn = earlyCompletion
        ? planAfterEarlyCompletion
        : interview.plan();
    boolean dimensionCompleted = naturallyCompletes || earlyCompletion;
    List<ProbeGap> nextProbeGaps = dimensionCompleted
        ? List.of()
        : assessment.probeGaps();
    boolean lastTurn = interview.plan().isLastTurn(answer.turnIndex());
    DimensionBrief dimensionBrief = null;
    List<CandidateClaim> candidateClaims = List.of();
    if (dimensionCompleted && lastTurn) {
      // 末轮保持同步生成：维度小结与候选人声明是报告输入，必须随面试完成落库
      dimensionBrief = dimensionBriefService.summarize(
          sessionId,
          currentDimension,
          history.turns(),
          answer,
          history.llmProvider()
      );
      candidateClaims = candidateClaimExtractionService.extract(
          sessionId,
          currentDimension,
          history.turns(),
          answer,
          planningTaxonomy.catalog(),
          history.llmProvider()
      );
    } else if (dimensionCompleted) {
      // 非末轮：维度记忆异步生成，不阻塞出题；新小结从下一轮决策起可见
      answerExecutor.execute(() -> generateDimensionMemorySafely(
          sessionId,
          currentDimension,
          history.turns(),
          answer,
          history.llmProvider()
      ));
    }
    List<PracticeRecommendation> practiceRecommendations = lastTurn
        ? practiceRecommendationService.recommend(
            sessionId,
            currentDimension,
            assessment
        )
        : List.of();
    NextDecision nextDecision;
    if (lastTurn && answer.codeSubmission() == null) {
      nextDecision = new NextDecision(
          ReActResult.withoutTools(RespondAction.finish(
              "面试已覆盖全部规划维度。",
              "规划轮次已全部完成"
          )),
          NextTurnProvenanceDraft.planned()
      );
    } else {
      PlannedDimension nextDimension = lastTurn
          ? currentDimension
          : planForNextTurn.dimensionForTurn(answer.turnIndex() + 1);
      sink.onStage(AnswerEventSink.AnswerStage.GENERATING);
      WorkingMemorySelection workingMemory = contextAssembler.nextQuestionWorkingMemory(
          new NextQuestionWorkingMemoryInput(
              sessionId,
              answer.turnIndex() + 1,
              new TopicKey(nextDimension.suggestedSkill(), nextDimension.focusId()),
              nextProbeGaps,
              workingMemoryFactSource.findProbeGaps(
                  new MemoryOwner(null, history.candidateId()),
                  sessionId
              ),
              history.turns()
          )
      );
      nextDecision = new NextDecision(
          runDecision(
              request(new InterviewerDecisionInput(
                  sessionId,
                  history.llmProvider(),
                  history.jd(),
                  history.resume(),
                  history.session().maxTurns(),
                  nextDimension,
                  history.turns(),
                  answer,
                  workingMemory.snapshot()
              )),
              sink.deltaSink()
          ),
          workingMemory.provenance()
      );
    }
    ReActResult decision = nextDecision.decision();
    if (answer.codeSubmission() != null) {
      List<ToolExecution> sandboxSubmissions = decision.toolExecutions().stream()
          .filter(execution -> SandboxSubmitTool.NAME.equals(execution.toolName()))
          .toList();
      if (sandboxSubmissions.size() != 1
          || sandboxSubmissions.getFirst().outcome() != ToolExecutionOutcome.PENDING
          || sandboxSubmissions.getFirst().turnIndex() != answer.turnIndex()) {
        throw new BusinessException(
            ErrorCode.AI_SERVICE_ERROR,
            "算法代码回答必须产生一个异步判题任务"
        );
      }
    }
    Optional<DepthLevel> previousDepth = currentDimension.completedTurns() == 0
        ? Optional.empty()
        : Optional.of(persistenceService.latestAssessmentDepth(
            sessionId,
            currentDimension.order()
        ));
    try {
      PlannedInterview updated = persistenceService.recordDecision(
          new AdaptiveDecisionPersistenceInput(
              sessionId,
              answer,
              decision.response(),
              decision.toolExecutions(),
              dimensionBrief,
              candidateClaims,
              assessment,
              assessmentEvidences,
              practiceRecommendations,
              nextDecision.provenance()
          )
      );
      algorithmAssessmentEvidenceService.attachAvailable(sessionId, answer.turnIndex());
      telemetry.assessmentRecorded(
          currentDimension.dimension(),
          assessment.depthLevel(),
          assessmentEvidences.size()
      );
      previousDepth.ifPresent(depthLevel -> telemetry.followUpAssessed(
          currentDimension.dimension(),
          depthLevel,
          assessment.depthLevel()
      ));
      return updated;
    } catch (OptimisticLockingFailureException e) {
      telemetry.stateConflict(sessionId, answer.turnIndex());
      throw new BusinessException(
          ErrorCode.BAD_REQUEST,
          "面试会话已被其他请求推进，请刷新后重试",
          e
      );
    }
  }

  public PlannedInterview submitAnswerForCandidate(
      String candidateId,
      String sessionId,
      CandidateAnswer answer
  ) {
    persistenceService.requireCandidateSession(candidateId, sessionId);
    return submitAnswer(sessionId, answer);
  }

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
    PlannedDimension dimension = interview.plan().dimensionForTurn(event.turnIndex());
    try {
      ReActResult decision = runDecision(new ReActRequest(
          sessionId,
          AgentRole.INTERVIEWER,
          interview.history().llmProvider(),
          contextAssembler.toolResult(new ToolResultContextInput(
              interview.history().jd(),
              interview.history().resume(),
              interview.history().session().maxTurns(),
              dimension.order(),
              dimension.dimension(),
              dimension.focus(),
              allowedSuggestedTools(dimension),
              dimension.suggestedSkill(),
              interview.history().turns(),
              event,
              toolResultWorkingMemory(interview, dimension, event),
              episodePromptMemoryService.select(
                  sessionId,
                  new TopicKey(dimension.suggestedSkill(), dimension.focusId())
              ),
              codeAnalysisContextService.findForSession(sessionId).orElse(null)
          ))
      ));
      if (decision.response().type() != AgentResponseType.ASK) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "工具结果事件只能生成追问");
      }
      persistenceService.completeToolResultEvent(
          sessionId,
          event,
          decision.response(),
          decision.toolExecutions()
      );
      return Optional.of(decision.response());
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

  private WorkingMemorySnapshot toolResultWorkingMemory(
      PlannedInterview interview,
      PlannedDimension dimension,
      ToolResultEvent event
  ) {
    int currentTurnIndex = Math.max(
        event.turnIndex() + 1,
        interview.history().session().currentTurn()
    );
    return contextAssembler.workingMemory(new WorkingMemoryInput(
        interview.history().session().id(),
        currentTurnIndex,
        new TopicKey(dimension.suggestedSkill(), dimension.focusId()),
        event.turnIndex(),
        TurnTriggerType.TOOL_RESULT,
        List.of(),
        interview.history().turns()
    ));
  }

  /**
   * 当算法评测结果到达后，基于运行结果重新评估该轮回答并替换原有评估。
   *
   * @param sessionId 会话 ID
   * @param turnIndex 需要重新评估的轮次
   * @param toolResult 算法沙箱评测结果原文
   */
  public void reassessAlgorithmResult(
      String sessionId,
      int turnIndex,
      String toolResult
  ) {
    PlannedInterview interview = persistenceService.get(sessionId);
    AdaptiveInterviewTurn turn = interview.history().turns().stream()
        .filter(candidate -> candidate.turnIndex() == turnIndex)
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试轮次不存在"));
    PlannedDimension dimension = interview.plan().dimensionForTurn(turnIndex);
    AssessmentDecision assessment = assessmentAgent.assess(
        new AssessmentRequest(
            sessionId,
            turnIndex,
            AssessmentContext.algorithmResult(
                dimension.dimension(),
                dimension.focus(),
                turn.question(),
                turn.answer(),
                toolResult
            ),
            skillService.buildEvaluationReferenceSection(
                dimension.suggestedSkill()
            )
        ),
        interview.history().llmProvider()
    );
    List<ValidatedAssessmentEvidence> evidences = assessmentEvidenceValidator.validate(
        sessionId,
        turnIndex,
        turn.answer(),
        assessment.evidenceQuotes().stream()
            .map(AssessmentEvidenceCandidate::quote)
            .toList()
    );
    persistenceService.replaceAssessment(sessionId, turnIndex, assessment, evidences);
    telemetry.assessmentRecorded(
        dimension.dimension(),
        assessment.depthLevel(),
        evidences.size()
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

  private ReActRequest request(InterviewerDecisionInput input) {
    return new ReActRequest(
        input.sessionId(),
        AgentRole.INTERVIEWER,
        input.llmProvider(),
        contextAssembler.interviewer(new InterviewerContextInput(
            input.jd(),
            input.resume(),
            input.maxTurns(),
            input.dimension().order(),
            input.dimension().dimension(),
            input.dimension().focus(),
            allowedSuggestedTools(input.dimension()),
            input.dimension().suggestedSkill(),
            input.turns(),
            input.candidateAnswer(),
            input.workingMemory(),
            episodePromptMemoryService.select(
                input.sessionId(),
                input.workingMemory().currentTopic()
            ),
            codeAnalysisContextService.findForSession(input.sessionId()).orElse(null)
        ))
    );
  }

  private boolean completesDimension(PlannedDimension dimension) {
    return dimension.completedTurns() + 1 == dimension.allocatedTurns();
  }

  /**
   * 计划中的建议工具只是模型提案，按角色白名单裁决后再下发给面试官上下文，
   * 避免模型被引导去调用未注册的工具。
   */
  private List<String> allowedSuggestedTools(PlannedDimension dimension) {
    return dimension.suggestedTools().stream()
        .filter(roleRegistry.get(AgentRole.INTERVIEWER).allowedTools()::contains)
        .toList();
  }

  private ReActResult runDecision(ReActRequest request) {
    return runDecision(request, null);
  }

  private ReActResult runDecision(ReActRequest request, Consumer<String> deltaSink) {
    long startedNanos = System.nanoTime();
    int inputTurn = request.inputTurnIndex();
    try {
      ReActResult result = runtime.runStreaming(
          request,
          roleRegistry.get(request.role()).budget(),
          deltaSink
      );
      telemetry.decisionSucceeded(result.response().type(), startedNanos);
      return result;
    } catch (BusinessException e) {
      telemetry.decisionFailed(
          request.sessionId(),
          inputTurn,
          e.getCode(),
          startedNanos
      );
      throw e;
    }
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
}
