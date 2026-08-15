package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentEvidenceCandidate;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.assessment.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

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

  public PlannedInterview create(
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    return create(null, candidateId, jd, resume, llmProvider);
  }

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
          briefsForNextDecision(interview.dimensionBriefs(), dimensionBrief)
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

  public PlannedInterview get(String sessionId) {
    return persistenceService.get(sessionId);
  }

  public PlannedInterview getForTenant(String tenantId, String sessionId) {
    return persistenceService.getForTenant(tenantId, sessionId);
  }

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
              interview.dimensionBriefs()
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
      List<DimensionBrief> dimensionBriefs
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
            dimensionBriefs
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
