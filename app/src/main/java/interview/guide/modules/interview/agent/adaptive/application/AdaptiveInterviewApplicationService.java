package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.planning.PlannerContext;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticePlanningMemory;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.llmprovider.service.CandidateChatProvider;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 自适应面试应用服务，负责创建会话、提交回答和查询聚合。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveInterviewApplicationService {

  private final AdaptiveInterviewPersistenceService persistenceService;
  private final AdaptiveAgentTelemetry telemetry;
  private final PlanningAgent planningAgent;
  private final ContextAssembler contextAssembler;
  private final PlanningTaxonomy planningTaxonomy;
  private final PracticeMemoryService practiceMemoryService;
  private final AlgorithmInterviewTelemetry algorithmTelemetry;
  private final CandidateLlmProviderService candidateProviderService;
  private final AdaptiveInterviewCreationTaskRunner creationExecutor;
  private final AdaptiveInterviewCreationService creationService;
  private final AdaptiveAgentProperties properties;
  private final AdaptiveAnswerProgressionService answerProgressionService;

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
    AdaptiveInterviewCreationService.InitialAgentRun run = initializeCreation(sessionId, input);
    PlannedInterview initialized = creationService.initialize(run);
    try {
      submitCreation(run, InterviewCreationEventSink.noop());
    } catch (RejectedExecutionException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "自适应面试创建任务提交失败", e);
    }
    return initialized;
  }

  private PlannedInterview createStreaming(
      InterviewCreationInput input,
      InterviewCreationEventSink sink
  ) {
    String sessionId = UUID.randomUUID().toString();
    AdaptiveInterviewCreationService.InitialAgentRun run = initializeCreation(sessionId, input);
    PlannedInterview initialized = creationService.initialize(run);
    sink.onCreated(initialized);
    try {
      submitCreation(run, sink);
    } catch (RejectedExecutionException e) {
      sink.onFailed("创建队列已满，请稍后重试");
    }
    return initialized;
  }

  private void submitCreation(
      AdaptiveInterviewCreationService.InitialAgentRun run,
      InterviewCreationEventSink sink
  ) {
    creationExecutor.submit(() -> {
      try {
        PlannedInterview completed = creationService.complete(run);
        if (sink.deltaSink() != null) {
          sink.deltaSink().accept(completed.history().turns().getFirst().question());
        }
        sink.onCompleted(completed);
      } catch (Exception e) {
        String message = readableFailure(e);
        log.error("自适应面试创建失败: sessionId={}", run.creation().sessionId(), e);
        sink.onFailed(message);
      }
    });
  }

  private AdaptiveInterviewCreationService.InitialAgentRun initializeCreation(
      String sessionId,
      InterviewCreationInput input
  ) {
    InterviewPlan plan = decidePlan(sessionId, input);
    return new AdaptiveInterviewCreationService.InitialAgentRun(
        input.toSessionCreation(sessionId),
        plan,
        properties.getDeadline()
    );
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
    try {
      InterviewPlan plan = InterviewPlan.decide(sessionId, proposal, input.settings());
      planningTaxonomy.validate(plan);
      return plan;
    } catch (BusinessException e) {
      telemetry.planRejected(sessionId, e.getCode());
      throw e;
    }
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

  private String readableFailure(Exception e) {
    String message = e instanceof BusinessException businessException
        ? businessException.getMessage()
        : e.getMessage();
    return message == null || message.isBlank() ? "创建链路未知异常" : message;
  }

  public PlannedInterview submitAnswer(String sessionId, CandidateAnswer answer) {
    return submitAnswer(new AnswerSubmissionInput(
        null,
        sessionId,
        answer,
        AnswerEventSink.noop()
    ));
  }

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
    PlannedInterview interview = input.tenantId() == null
        ? persistenceService.get(sessionId)
        : persistenceService.getForTenant(input.tenantId(), sessionId);
    algorithmTelemetry.interviewTurnSubmitted(sessionId);
    MemoryOwner owner = new MemoryOwner(input.tenantId(), interview.history().candidateId());
    answerProgressionService.advance(
        new AdaptiveAnswerProgressionService.AnswerProgressionCommand(
            owner,
            interview,
            new AdaptiveAnswerProgressionService.Submission(
                input.answer(), input.sink(), properties.getDeadline())
        )
    );
    return input.tenantId() == null
        ? persistenceService.get(sessionId)
        : persistenceService.getForTenant(input.tenantId(), sessionId);
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

  public PlannedInterview getForTenant(String tenantId, String sessionId) {
    return persistenceService.getForTenant(tenantId, sessionId);
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

  private record AnswerSubmissionInput(
      String tenantId,
      String sessionId,
      CandidateAnswer answer,
      AnswerEventSink sink
  ) {}
}
