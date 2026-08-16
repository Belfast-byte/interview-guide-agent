package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 自适应面试应用服务，是面试流程的总编排入口，负责创建会话、提交回答、生成报告和回填评估。
 */
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewApplicationService {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final BoundedReActRuntime runtime;
  private final AgentRoleRegistry roleRegistry;
  private final AdaptiveAgentTelemetry telemetry;
  private final PlanningAgent planningAgent;
  private final ContextAssembler contextAssembler;
  private final DimensionBriefService dimensionBriefService;
  private final CandidateMemoryService candidateMemoryService;
  private final PlanningTaxonomy planningTaxonomy;
  private final CandidateClaimExtractionService candidateClaimExtractionService;
  private final DepthAssessmentAgent assessmentAgent;
  private final AssessmentEvidenceValidator assessmentEvidenceValidator;
  private final PracticeRecommendationService practiceRecommendationService;
  private final AlgorithmAssessmentEvidenceService algorithmAssessmentEvidenceService;
  private final AlgorithmInterviewTelemetry algorithmTelemetry;
  private final CodeAnalysisInterviewContextService codeAnalysisContextService;
  private final InterviewSkillService skillService;

  /**
   * 创建一次非租户维度的自适应面试：规划面试计划并生成首轮决策。
   *
   * @param candidateId 候选人 ID
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param llmProvider 使用的 LLM 供应商
   * @return 已创建的自适应面试
   */
  public PlannedInterview create(
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    return create(null, candidateId, jd, resume, llmProvider);
  }

  /**
   * 创建指定租户下的自适应面试，用于多租户隔离场景。
   *
   * @param tenantId 租户 ID
   * @param candidateId 候选人 ID
   * @param jd 职位描述
   * @param resume 候选人简历
   * @param llmProvider 使用的 LLM 供应商
   * @return 已创建的自适应面试
   */
  public PlannedInterview createForTenant(
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    return create(tenantId, candidateId, jd, resume, llmProvider);
  }

  private PlannedInterview create(
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    String sessionId = UUID.randomUUID().toString();
    PlanProposal proposal = planningAgent.propose(
        new PlanningRequest(sessionId, contextAssembler.planner(
            jd,
            resume,
            tenantId == null
                ? candidateMemoryService.coveredTopics(candidateId)
                : candidateMemoryService.coveredTopics(tenantId, candidateId),
            tenantId == null
                ? candidateMemoryService.unverifiedClaims(candidateId)
                : candidateMemoryService.unverifiedClaims(tenantId, candidateId),
            planningTaxonomy.catalog()
        )),
        llmProvider
    );
    InterviewPlan plan;
    try {
      plan = InterviewPlan.decide(sessionId, proposal);
      planningTaxonomy.validate(plan);
    } catch (BusinessException e) {
      telemetry.planRejected(sessionId, e.getCode());
      throw e;
    }
    PlannedDimension firstDimension = plan.dimensionForTurn(1);
    ReActResult firstDecision = runDecision(request(
        sessionId,
        llmProvider,
        jd,
        resume,
        plan.maxTurns(),
        firstDimension,
        List.of(),
        null,
        List.of(),
        List.of()
    ));
    if (tenantId == null) {
      return persistenceService.create(
          sessionId,
          candidateId,
          jd,
          resume,
          llmProvider,
          plan,
          firstDecision.response(),
          firstDecision.toolExecutions()
      );
    }
    return persistenceService.createForTenant(
        tenantId,
        sessionId,
        candidateId,
        jd,
        resume,
        llmProvider,
        plan,
        firstDecision.response(),
        firstDecision.toolExecutions()
    );
  }

  /**
   * 提交候选人回答：执行深度评估、持久化轮次，并让面试官 Agent 决定下一轮动作。
   *
   * @param sessionId 会话 ID
   * @param answer 候选人回答
   * @return 推进后的面试状态
   */
  public PlannedInterview submitAnswer(String sessionId, CandidateAnswer answer) {
    PlannedInterview interview = persistenceService.get(sessionId);
    AdaptiveInterviewHistory history = interview.history();
    history.session().assertCanAnswer(answer);
    PlannedDimension currentDimension = interview.plan().dimensionForTurn(answer.turnIndex());
    algorithmTelemetry.interviewTurnSubmitted(sessionId);
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
            skillService.buildEvaluationReferenceSectionSafe(
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
    boolean dimensionCompleted = completesDimension(currentDimension);
    List<ProbeGap> nextProbeGaps = dimensionCompleted
        ? List.of()
        : assessment.probeGaps();
    DimensionBrief dimensionBrief = dimensionCompleted
        ? dimensionBriefService.summarize(
            sessionId,
            currentDimension,
            history.turns(),
            answer,
            history.llmProvider()
        )
        : null;
    List<CandidateClaim> candidateClaims = dimensionCompleted
        ? candidateClaimExtractionService.extract(
            sessionId,
            currentDimension,
            history.turns(),
            answer,
            planningTaxonomy.catalog(),
            history.llmProvider()
        )
        : List.of();
    boolean lastTurn = interview.plan().isLastTurn(answer.turnIndex());
    List<PracticeRecommendation> practiceRecommendations = lastTurn
        ? practiceRecommendationService.recommend(
            sessionId,
            currentDimension,
            assessment
        )
        : List.of();
    ReActResult decision;
    if (lastTurn && answer.codeSubmission() == null) {
      decision = ReActResult.withoutTools(RespondAction.finish(
          "面试已覆盖全部规划维度。",
          "规划轮次已全部完成"
      ));
    } else {
      PlannedDimension nextDimension = lastTurn
          ? currentDimension
          : interview.plan().dimensionForTurn(answer.turnIndex() + 1);
      decision = runDecision(request(
          sessionId,
          history.llmProvider(),
          history.jd(),
          history.resume(),
          history.session().maxTurns(),
          nextDimension,
          history.turns(),
          answer,
          briefsForNextDecision(interview.dimensionBriefs(), dimensionBrief),
          nextProbeGaps
      ));
    }
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
          sessionId,
          answer,
          decision.response(),
          decision.toolExecutions(),
          dimensionBrief,
          candidateClaims,
          assessment,
          assessmentEvidences,
          practiceRecommendations
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

  /**
   * 获取指定自适应面试的当前状态。
   *
   * @param sessionId 会话 ID
   * @return 面试聚合
   */
  public PlannedInterview get(String sessionId) {
    return persistenceService.get(sessionId);
  }

  /**
   * 在代码分析完成后重新规划面试：用代码分析结论替换初始计划并重新生成首轮决策。
   *
   * @param sessionId 会话 ID
   * @return 重新规划后的面试
   */
  public PlannedInterview replanWithCodeAnalysis(String sessionId) {
    PlannedInterview current = persistenceService.get(sessionId);
    AdaptiveInterviewHistory history = current.history();
    PlanProposal proposal = planningAgent.propose(
        new PlanningRequest(
            sessionId,
            contextAssembler.planner(
                history.jd(),
                history.resume(),
                candidateMemoryService.coveredTopics(history.candidateId()),
                candidateMemoryService.unverifiedClaims(history.candidateId()),
                planningTaxonomy.catalog()
            ),
            codeAnalysisContextService.findPlanningForSession(sessionId)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "代码分析尚未完成"
                ))
        ),
        history.llmProvider()
    );
    InterviewPlan plan = InterviewPlan.decide(sessionId, proposal);
    planningTaxonomy.validate(plan);
    PlannedDimension firstDimension = plan.dimensionForTurn(1);
    ReActResult firstDecision = runDecision(request(
        sessionId,
        history.llmProvider(),
        history.jd(),
        history.resume(),
        plan.maxTurns(),
        firstDimension,
        List.of(),
        null,
        List.of(),
        List.of()
    ));
    return persistenceService.replaceInitialPlan(
        sessionId,
        plan,
        firstDecision.response(),
        firstDecision.toolExecutions()
    );
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
   * 处理异步工具结果事件：预留事件、让面试官生成追问，并持久化结果。
   *
   * @param sessionId 会话 ID
   * @param event 工具结果事件
   * @return 生成的追问响应；若事件已被其他请求处理则返回空
   */
  public Optional<RespondAction> handleToolResult(
      String sessionId,
      ToolResultEvent event
  ) {
    PlannedInterview interview = persistenceService.get(sessionId);
    if (!persistenceService.reserveToolResultEvent(sessionId, event)) {
      return Optional.empty();
    }
    PlannedDimension dimension = interview.plan().dimensionForTurn(event.turnIndex());
    try {
      ReActResult decision = runDecision(new ReActRequest(
          sessionId,
          AgentRole.INTERVIEWER,
          interview.history().llmProvider(),
          contextAssembler.toolResult(
              interview.history().jd(),
              interview.history().resume(),
              interview.history().session().maxTurns(),
              dimension.order(),
              dimension.dimension(),
              dimension.focus(),
              dimension.suggestedTools(),
              dimension.suggestedSkill(),
              interview.history().turns(),
              event,
              interview.dimensionBriefs(),
              codeAnalysisContextService.findForSession(sessionId).orElse(null)
          )
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
    } catch (BusinessException e) {
      persistenceService.discardToolResultReservation(event);
      throw e;
    }
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
        .orElseThrow();
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
            skillService.buildEvaluationReferenceSectionSafe(
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

  private ReActRequest request(
      String sessionId,
      String llmProvider,
      String jd,
      String resume,
      int maxTurns,
      PlannedDimension dimension,
      List<AdaptiveInterviewTurn> turns,
      CandidateAnswer candidateAnswer,
      List<DimensionBrief> dimensionBriefs,
      List<ProbeGap> probeGaps
  ) {
    return new ReActRequest(
        sessionId,
        AgentRole.INTERVIEWER,
        llmProvider,
        contextAssembler.interviewer(
            jd,
            resume,
            maxTurns,
            dimension.order(),
            dimension.dimension(),
            dimension.focus(),
            dimension.suggestedTools(),
            dimension.suggestedSkill(),
            turns,
            candidateAnswer,
            probeGaps,
            dimensionBriefs,
            codeAnalysisContextService.findForSession(sessionId).orElse(null)
        )
    );
  }

  private boolean completesDimension(PlannedDimension dimension) {
    return dimension.completedTurns() + 1 == dimension.allocatedTurns();
  }

  private List<DimensionBrief> briefsForNextDecision(
      List<DimensionBrief> persistedBriefs,
      DimensionBrief newBrief
  ) {
    if (newBrief == null) {
      return persistedBriefs;
    }
    return Stream.concat(persistedBriefs.stream(), Stream.of(newBrief))
        .toList();
  }

  private ReActResult runDecision(ReActRequest request) {
    long startedNanos = System.nanoTime();
    int inputTurn = request.inputTurnIndex();
    try {
      ReActResult result = runtime.run(
          request,
          roleRegistry.get(request.role()).budget()
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
}
