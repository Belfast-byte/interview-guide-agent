package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendation;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeStatus;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.EpisodePromptFact;
import interview.guide.modules.interview.agent.adaptive.core.context.CandidateClaimType;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveDecisionPersistenceInput;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActResult;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import interview.guide.modules.interview.agent.adaptive.tool.SandboxSubmitTool;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateChatProvider;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveInterviewApplicationServiceTest {

  @Mock
  private AdaptiveInterviewPersistenceService persistenceService;

  @Mock
  private BoundedReActRuntime runtime;

  @Mock
  private AdaptiveAgentTelemetry telemetry;

  @Mock
  private PlanningAgent planningAgent;

  @Mock
  private DimensionBriefService dimensionBriefService;

  @Mock
  private CandidateMemoryService candidateMemoryService;

  @Mock
  private WorkingMemoryFactSource workingMemoryFactSource;

  @Mock
  private EpisodePromptMemoryService episodePromptMemoryService;

  @Mock
  private PlanningTaxonomy planningTaxonomy;

  @Mock
  private CandidateClaimExtractionService candidateClaimExtractionService;

  @Mock
  private PracticeRecommendationService practiceRecommendationService;

  @Mock
  private AlgorithmAssessmentEvidenceService algorithmAssessmentEvidenceService;

  @Mock
  private AlgorithmInterviewTelemetry algorithmTelemetry;

  @Mock
  private CodeAnalysisInterviewContextService codeAnalysisContextService;

  @Mock
  private InterviewSkillService skillService;

  @Mock
  private CandidateLlmProviderService candidateProviderService;

  @Mock
  private AdaptiveInterviewAnswerExecutor answerExecutor;

  private AdaptiveInterviewApplicationService service;

  @BeforeEach
  void setUp() {
    // 维度记忆异步任务在测试中同步执行，保持断言时序确定
    lenient().doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(answerExecutor).execute(any(Runnable.class));
    lenient().when(episodePromptMemoryService.select(anyString(), any()))
        .thenReturn(List.of());
    lenient().when(workingMemoryFactSource.findProbeGaps(any(), anyString()))
        .thenReturn(List.of());
    service = serviceWithAssessmentAgent(assessmentAgent());
  }

  @Test
  @DisplayName("候选人创建面试时固化所选 Provider 名称和模型")
  void shouldResolveAndSnapshotCandidateProvider() {
    UUID candidateId = UUID.randomUUID();
    PlannedInterview expected = interviewAtTurn(1);
    when(candidateProviderService.resolveChatProvider(candidateId, "provider-2"))
        .thenReturn(new CandidateChatProvider("provider-2", "我的模型", "chat-model"));
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(expected);
    AdaptiveInterviewApplicationService lazyService = serviceWithAssessmentAgent(
        assessmentAgent(),
        task -> {
        }
    );

    PlannedInterview actual = lazyService.createForCandidate(
        candidateId,
        "JD",
        "Resume",
        "provider-2"
    );

    assertThat(actual).isSameAs(expected);
    ArgumentCaptor<AdaptiveSessionCreation> captor = ArgumentCaptor.forClass(
        AdaptiveSessionCreation.class
    );
    verify(persistenceService).createSkeleton(captor.capture());
    assertThat(captor.getValue()).satisfies(creation -> {
      assertThat(creation.candidateId()).isEqualTo(candidateId.toString());
      assertThat(creation.llmProviderId()).isEqualTo("provider-2");
      assertThat(creation.llmProviderNameSnapshot()).isEqualTo("我的模型");
      assertThat(creation.llmModelSnapshot()).isEqualTo("chat-model");
    });
  }

  private AdaptiveInterviewApplicationService serviceWithAssessmentAgent(
      DepthAssessmentAgent assessmentAgent
  ) {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    return new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(properties),
        telemetry,
        planningAgent,
        new ContextAssembler(),
        workingMemoryFactSource,
        dimensionBriefService,
        candidateMemoryService,
        episodePromptMemoryService,
        planningTaxonomy,
        candidateClaimExtractionService,
        assessmentAgent,
        evidenceValidator(),
        practiceRecommendationService,
        algorithmAssessmentEvidenceService,
        algorithmTelemetry,
        codeAnalysisContextService,
        skillService,
        candidateProviderService,
        task -> task.run(),
        answerExecutor
    );
  }

  private AdaptiveInterviewApplicationService serviceWithAssessmentAgent(
      DepthAssessmentAgent assessmentAgent,
      AdaptiveInterviewCreationTaskRunner creationExecutor
  ) {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    return new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(properties),
        telemetry,
        planningAgent,
        new ContextAssembler(),
        workingMemoryFactSource,
        dimensionBriefService,
        candidateMemoryService,
        episodePromptMemoryService,
        planningTaxonomy,
        candidateClaimExtractionService,
        assessmentAgent,
        evidenceValidator(),
        practiceRecommendationService,
        algorithmAssessmentEvidenceService,
        algorithmTelemetry,
        codeAnalysisContextService,
        skillService,
        candidateProviderService,
        creationExecutor,
        answerExecutor
    );
  }

  @Test
  @DisplayName("创建立即返回骨架，后台按骨架→规划→首题→落库顺序完成")
  void shouldReturnSkeletonThenGenerateFirstTurnInBackground() {
    PlanningMemoryFixture memory = planningMemory();
    EpisodePromptFact historicalFact = episodePromptFact();
    PlannedInterview expected = interviewAtTurn(1);
    stubFirstTurn(memory, historicalFact, expected);

    PlannedInterview actual = service.create("candidate-1", "JD", "Resume", null);

    assertThat(actual).isSameAs(expected);
    assertFirstTurnContexts(memory, historicalFact);
    verifyCreationSequence();
  }

  private PlanningMemoryFixture planningMemory() {
    return new PlanningMemoryFixture(
        new CoveredTopic("java-backend", "REDIS"),
        new UnverifiedClaim(
            CandidateClaimType.PROJECT_EXPERIENCE,
            "java-backend",
            "PROJECT"
        ),
        new PlanningSkill("java-backend", List.of("JAVA", "REDIS", "PROJECT"))
    );
  }

  private EpisodePromptFact episodePromptFact() {
    return new EpisodePromptFact(
        "java-backend",
        "JAVA",
        DepthLevel.L3,
        List.of("MISSING_CONCURRENCY_ANALYSIS"),
        List.of("STRUCTURED_REASONING"),
        LocalDateTime.of(2026, 8, 1, 10, 0)
    );
  }

  private void stubFirstTurn(
      PlanningMemoryFixture memory,
      EpisodePromptFact historicalFact,
      PlannedInterview expected
  ) {
    when(candidateMemoryService.coveredTopics("candidate-1"))
        .thenReturn(List.of(memory.coveredTopic()));
    when(candidateMemoryService.unverifiedClaims("candidate-1"))
        .thenReturn(List.of(memory.unverifiedClaim()));
    when(planningTaxonomy.catalog()).thenReturn(List.of(memory.planningSkill()));
    when(planningAgent.propose(any(), any())).thenReturn(proposal());
    when(episodePromptMemoryService.select(
        anyString(), eq(new TopicKey("java-backend", "JAVA"))
    )).thenReturn(List.of(historicalFact));
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(expected);
    when(persistenceService.completeCreation(
        anyString(), any(InterviewPlan.class), any(RespondAction.class), anyList()
    )).thenReturn(expected);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(RespondAction.ask("第一题？", "验证基础")));
  }

  private void assertFirstTurnContexts(
      PlanningMemoryFixture memory,
      EpisodePromptFact historicalFact
  ) {
    ArgumentCaptor<PlanningRequest> planningRequest = ArgumentCaptor.forClass(
        PlanningRequest.class
    );
    verify(planningAgent).propose(planningRequest.capture(), any());
    assertThat(planningRequest.getValue().context().coveredTopics())
        .containsExactly(memory.coveredTopic());
    assertThat(planningRequest.getValue().context().skillCatalog())
        .containsExactly(memory.planningSkill());
    assertThat(planningRequest.getValue().context().unverifiedClaims())
        .containsExactly(memory.unverifiedClaim());
    ArgumentCaptor<ReActRequest> interviewerRequest = ArgumentCaptor.forClass(
        ReActRequest.class
    );
    verify(runtime).runStreaming(
        interviewerRequest.capture(), any(ReActBudget.class), isNull()
    );
    assertThat(interviewerRequest.getValue().interviewerContext().episodeHistory())
        .containsExactly(historicalFact);
  }

  private void verifyCreationSequence() {
    verify(planningTaxonomy).validate(any(InterviewPlan.class));
    verify(telemetry).decisionSucceeded(eq(AgentResponseType.ASK), anyLong());
    InOrder order = inOrder(persistenceService, planningAgent, runtime);
    order.verify(persistenceService).createSkeleton(any(AdaptiveSessionCreation.class));
    order.verify(planningAgent).propose(any(), any());
    order.verify(runtime).runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull());
    order.verify(persistenceService).completeCreation(
        anyString(), any(InterviewPlan.class), any(RespondAction.class), anyList()
    );
  }

  private record PlanningMemoryFixture(
      CoveredTopic coveredTopic,
      UnverifiedClaim unverifiedClaim,
      PlanningSkill planningSkill
  ) {}

  @Test
  @DisplayName("流式创建按骨架→首题增量→完成会话顺序推送")
  void shouldStreamFirstQuestionDuringCandidateCreation() {
    UUID candidateId = UUID.randomUUID();
    PlannedInterview skeleton = interviewAtTurn(1);
    PlannedInterview completed = interviewAtTurn(1);
    Consumer<String> deltaSink = delta -> {};
    InterviewCreationEventSink sink = org.mockito.Mockito.mock(
        InterviewCreationEventSink.class
    );
    when(sink.deltaSink()).thenReturn(deltaSink);
    when(candidateProviderService.resolveChatProvider(candidateId, "provider-2"))
        .thenReturn(new CandidateChatProvider("provider-2", "我的模型", "chat-model"));
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(skeleton);
    when(planningAgent.propose(any(), any())).thenReturn(proposal());
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), same(deltaSink)))
        .thenReturn(ReActResult.withoutTools(RespondAction.ask("第一题？", "验证基础")));
    when(persistenceService.completeCreation(
        anyString(), any(InterviewPlan.class), any(RespondAction.class), anyList()
    )).thenReturn(completed);

    service.createForCandidateStreaming(
        new CandidateInterviewCreationCommand(candidateId, "JD", "Resume", "provider-2"),
        sink
    );

    InOrder order = inOrder(sink, runtime, persistenceService);
    order.verify(sink).onCreated(skeleton);
    order.verify(runtime).runStreaming(
        any(ReActRequest.class), any(ReActBudget.class), same(deltaSink)
    );
    order.verify(persistenceService).completeCreation(
        anyString(), any(InterviewPlan.class), any(RespondAction.class), anyList()
    );
    order.verify(sink).onCompleted(completed);
  }

  @Test
  @DisplayName("骨架落库后创建请求立即返回，不等规划完成")
  void shouldReturnSkeletonBeforePlanningRuns() {
    PlannedInterview skeleton = interviewAtTurn(1);
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(skeleton);
    AdaptiveInterviewApplicationService lazyService = serviceWithAssessmentAgent(
        assessmentAgent(),
        task -> {
        }
    );

    PlannedInterview actual = lazyService.create("candidate-1", "JD", "Resume", null);

    assertThat(actual).isSameAs(skeleton);
    verifyNoInteractions(planningAgent);
    verify(persistenceService, never()).completeCreation(
        anyString(), any(), any(), anyList()
    );
  }

  @Test
  @DisplayName("创建队列打满时骨架置 FAILED 并向调用方报错")
  void shouldFailSkeletonWhenCreationQueueIsFull() {
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(interviewAtTurn(1));
    AdaptiveInterviewApplicationService rejectingService = serviceWithAssessmentAgent(
        assessmentAgent(),
        task -> {
          throw new java.util.concurrent.RejectedExecutionException("queue full");
        }
    );

    assertThatThrownBy(() -> rejectingService.create("candidate-1", "JD", "Resume", null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("创建任务提交失败");

    verify(persistenceService).failCreation(anyString(), eq("创建队列已满，请稍后重试"));
    verifyNoInteractions(planningAgent);
  }

  @Test
  @DisplayName("规划失败时骨架置为 FAILED 且不生成首题")
  void shouldFailSkeletonWhenPlanningFails() {
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(interviewAtTurn(1));
    when(planningAgent.propose(any(), any())).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "规划失败"
    ));

    PlannedInterview created = service.create("candidate-1", "JD", "Resume", null);

    assertThat(created).isNotNull();
    verify(persistenceService).failCreation(anyString(), eq("规划失败"));
    verify(runtime, never()).runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull());
    verify(persistenceService, never()).completeCreation(
        anyString(), any(), any(), anyList()
    );
  }

  @Test
  @DisplayName("非法规划被代码拒绝并把骨架置为 FAILED")
  void shouldRejectInvalidPlanAndFailSkeleton() {
    when(persistenceService.createSkeleton(any(AdaptiveSessionCreation.class)))
        .thenReturn(interviewAtTurn(1));
    when(planningAgent.propose(any(), any())).thenReturn(new PlanProposal(List.of()));

    PlannedInterview created = service.create("candidate-1", "JD", "Resume", null);

    assertThat(created).isNotNull();
    verify(telemetry).planRejected(anyString(), anyInt());
    verify(persistenceService).failCreation(
        anyString(),
        org.mockito.ArgumentMatchers.contains("1 到 12")
    );
    verify(persistenceService, never()).completeCreation(
        anyString(), any(), any(), anyList()
    );
  }

  @Test
  @DisplayName("模型失败时不写入回答和下一题")
  void shouldNotAdvanceWhenModelFails() {
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(1));
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型失败"));

    assertThatThrownBy(() -> service.submitAnswer(
        "session-1",
        new CandidateAnswer(1, "回答")
    )).isInstanceOf(BusinessException.class)
        .hasMessage("模型失败");

    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
    verify(telemetry).decisionFailed(eq("session-1"), eq(1), anyInt(), anyLong());
  }

  @Test
  @DisplayName("评估产出的追问缺口进入同维度下一轮面试官上下文")
  void shouldPassProbeGapsToSameDimensionInterviewer() {
    ProbeGap gap = new ProbeGap("回答", "未说明失败场景");
    AdaptiveInterviewApplicationService gapService = serviceWithAssessmentAgent(
        new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
            DepthLevel.L2,
            0.8,
            "描述了应用",
            false,
            List.of("回答"),
            List.of(gap)
        ))
    );
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "回答");
    RespondAction action = RespondAction.ask("下一题？", "继续验证");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(skillService.buildEvaluationReferenceSection("java-backend"))
        .thenReturn("### Redis (REDIS)\n- 缓存穿透");
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    gapService.submitAnswer("session-1", answer);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().workingMemory().selectedGap())
        .isEqualTo(gap);
    assertThat(request.getValue().interviewerContext().workingMemory().triggerType())
        .isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(request.getValue().interviewerContext().workingMemory().followUpDepth())
        .isEqualTo(1);
    verify(skillService).buildEvaluationReferenceSection("java-backend");
  }

  @Test
  @DisplayName("当前维度完成后追问缺口不泄漏到下一维度")
  void shouldClearProbeGapsWhenDimensionCompletes() {
    ProbeGap gap = new ProbeGap("第二轮回答", "未说明失败场景");
    AdaptiveInterviewApplicationService gapService = serviceWithAssessmentAgent(
        new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
            DepthLevel.L2,
            0.8,
            "描述了应用",
            false,
            List.of("第二轮回答"),
            List.of(gap)
        ))
    );
    PlannedInterview interview = interviewAtTurn(2);
    CandidateAnswer answer = new CandidateAnswer(2, "第二轮回答");
    RespondAction action = RespondAction.ask("项目经验问题？", "切换维度");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(dimensionBriefService.summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    )).thenReturn(null);
    when(planningTaxonomy.catalog()).thenReturn(List.of());
    when(candidateClaimExtractionService.extract(
        eq("session-1"), any(), anyList(), eq(answer), anyList(), nullable(String.class)
    )).thenReturn(List.of());
    when(persistenceService.latestAssessmentDepth("session-1", 0))
        .thenReturn(DepthLevel.L1);
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    gapService.submitAnswer("session-1", answer);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().workingMemory().selectedGap()).isNull();
    assertThat(request.getValue().interviewerContext().targetDimension()).isEqualTo("项目经验");
    // 非末轮维度完成：记忆异步生成单独落库，当轮决策拿到的是空小结/空声明
    verify(persistenceService).saveDimensionMemory("session-1", null, List.of());
    verify(persistenceService).recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("L4 评估提前完成当前维度并让下一轮进入后续维度")
  void shouldCompleteDimensionEarlyOnL4Assessment() {
    AdaptiveInterviewApplicationService l4Service = serviceWithAssessmentAgent(
        new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
            DepthLevel.L4,
            0.9,
            "已充分验证",
            false,
            List.of("第一轮回答"),
            List.of()
        ))
    );
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "第一轮回答");
    RespondAction action = RespondAction.ask("项目经验问题？", "维度提前完成");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(skillService.buildEvaluationReferenceSection("java-backend"))
        .thenReturn("### Redis (REDIS)\n- 缓存穿透");
    when(dimensionBriefService.summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    )).thenReturn(null);
    when(planningTaxonomy.catalog()).thenReturn(List.of());
    when(candidateClaimExtractionService.extract(
        eq("session-1"), any(), anyList(), eq(answer), anyList(), nullable(String.class)
    )).thenReturn(List.of());
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    l4Service.submitAnswer("session-1", answer);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().targetDimension())
        .isEqualTo("项目经验");
    assertThat(request.getValue().interviewerContext().workingMemory().selectedGap()).isNull();
    // 提前完成的维度记忆走异步任务生成并单独落库
    verify(dimensionBriefService).summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    );
    verify(persistenceService).saveDimensionMemory("session-1", null, List.of());
  }

  @Test
  @DisplayName("末维度提前完成是空操作时不提前写维度小结和声明")
  void shouldNotPersistBriefWhenEarlyCompletionIsNoOpOnLastDimension() {
    AdaptiveInterviewApplicationService l4Service = serviceWithAssessmentAgent(
        new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
            DepthLevel.L4,
            0.9,
            "已充分验证",
            false,
            List.of("末维度第一轮回答"),
            List.of()
        ))
    );
    PlannedInterview interview = interviewAtLastDimensionFirstTurn();
    CandidateAnswer answer = new CandidateAnswer(5, "末维度第一轮回答");
    RespondAction action = RespondAction.ask("末维度第二题？", "继续验证");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    l4Service.submitAnswer("session-1", answer);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().targetDimension())
        .isEqualTo("系统设计");
    verifyNoInteractions(dimensionBriefService);
    verifyNoInteractions(candidateClaimExtractionService);
  }

  @Test
  @DisplayName("代码回答必须通过 sandbox_submit 产生绑定原轮次的 Pending 结果")
  void shouldPersistPendingSandboxSubmissionForCodeAnswer() {
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(
        1,
        "class Main {}",
        new CandidateCodeSubmission("two-sum", "JAVA", "FULL")
    );
    RespondAction action = RespondAction.ask("先说明这段实现的复杂度？", "判题期间继续追问");
    ToolExecution pending = new ToolExecution(
        "invocation-1",
        SandboxSubmitTool.NAME,
        "提交候选人代码",
        "INTERVIEWER",
        1,
        "keys=[problemId, runMode]",
        "submission pending",
        "execution-1",
        "{\"submissionId\":\"execution-1\",\"status\":\"PENDING\"}",
        ToolExecutionOutcome.PENDING,
        5
    );
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(new ReActResult(action, List.of(pending)));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    service.submitAnswer("session-1", answer);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().currentCodeSubmission())
        .isEqualTo(answer);
    verify(persistenceService).recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("代码回答未产生异步判题任务时不推进会话")
  void shouldRejectCodeAnswerWithoutPendingSubmission() {
    CandidateAnswer answer = new CandidateAnswer(
        1,
        "class Main {}",
        new CandidateCodeSubmission("two-sum", "JAVA", "FULL")
    );
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(1));
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(RespondAction.ask("下一题？", "继续")));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("异步判题任务");

    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("评估模型失败时不调用面试官也不推进状态")
  void shouldNotAdvanceWhenAssessmentFails() {
    AdaptiveInterviewApplicationService failingService = serviceWithAssessmentAgent(
        new DepthAssessmentAgent((request, provider) -> {
          throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评估失败");
        })
    );
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(1));

    assertThatThrownBy(() -> failingService.submitAnswer(
        "session-1",
        new CandidateAnswer(1, "回答")
    )).isInstanceOf(BusinessException.class)
        .hasMessage("评估失败");

    verifyNoInteractions(runtime);
    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("末轮维度小结同步生成失败时不推进状态")
  void shouldNotAdvanceWhenLastTurnDimensionBriefFails() {
    PlannedInterview interview = interviewAtTurn(6);
    CandidateAnswer answer = new CandidateAnswer(6, "最终回答");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(dimensionBriefService.summarize(
        eq("session-1"),
        any(),
        anyList(),
        eq(answer),
        nullable(String.class)
    )).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "维度小结生成失败"));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessage("维度小结生成失败");

    verifyNoInteractions(runtime);
    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
    verify(persistenceService, never()).saveDimensionMemory(anyString(), any(), anyList());
  }

  @Test
  @DisplayName("末轮声明同步抽取失败时不推进状态")
  void shouldNotAdvanceWhenLastTurnClaimExtractionFails() {
    PlannedInterview interview = interviewAtTurn(6);
    CandidateAnswer answer = new CandidateAnswer(6, "最终回答");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(planningTaxonomy.catalog()).thenReturn(List.of());
    when(candidateClaimExtractionService.extract(
        eq("session-1"),
        any(),
        anyList(),
        eq(answer),
        anyList(),
        nullable(String.class)
    )).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "候选人声明抽取失败"));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessage("候选人声明抽取失败");

    verifyNoInteractions(runtime);
    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
    verify(persistenceService, never()).saveDimensionMemory(anyString(), any(), anyList());
  }

  @Test
  @DisplayName("非末轮维度记忆异步生成失败只记日志，不阻塞面试主流程")
  void shouldContinueWhenAsyncDimensionMemoryFails() {
    PlannedInterview interview = interviewAtTurn(2);
    CandidateAnswer answer = new CandidateAnswer(2, "第二轮回答");
    RespondAction action = RespondAction.ask("项目经验问题？", "切换维度");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(dimensionBriefService.summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    )).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "维度小结生成失败"));
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.latestAssessmentDepth("session-1", 0))
        .thenReturn(DepthLevel.L1);
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);

    PlannedInterview updated = service.submitAnswer("session-1", answer);

    assertThat(updated).isSameAs(interview);
    // 异步任务失败被吞掉：不写维度记忆，但当轮决策正常落库
    verify(persistenceService, never()).saveDimensionMemory(anyString(), any(), anyList());
    verify(persistenceService).recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("末轮维度完成仍同步生成小结和声明并随决策落库")
  void shouldGenerateDimensionMemorySynchronouslyOnLastTurn() {
    PlannedInterview interview = interviewAtTurn(6);
    CandidateAnswer answer = new CandidateAnswer(6, "最终回答");
    DimensionBrief brief = new DimensionBrief(
        "session-1", 2, "系统设计", "扩展边界", "能说明方案权衡", List.of(5, 6)
    );
    List<CandidateClaim> claims = List.of(new CandidateClaim(
        CandidateClaimType.PROJECT_EXPERIENCE, "system-design", "DISTRIBUTED", 6
    ));
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(dimensionBriefService.summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    )).thenReturn(brief);
    when(planningTaxonomy.catalog()).thenReturn(List.of());
    when(candidateClaimExtractionService.extract(
        eq("session-1"), any(), anyList(), eq(answer), anyList(), nullable(String.class)
    )).thenReturn(claims);
    when(practiceRecommendationService.recommend(eq("session-1"), any(), any()))
        .thenReturn(List.of());
    when(persistenceService.latestAssessmentDepth("session-1", 2))
        .thenReturn(DepthLevel.L1);
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);

    PlannedInterview updated = service.submitAnswer("session-1", answer);

    assertThat(updated).isSameAs(interview);
    verify(persistenceService).recordDecision(any(AdaptiveDecisionPersistenceInput.class));
    verify(persistenceService, never()).saveDimensionMemory(anyString(), any(), anyList());
    verify(answerExecutor, never()).execute(any(Runnable.class));
    verifyNoInteractions(runtime);
  }

  @Test
  @DisplayName("流式答题先鉴权，再按 ASSESSING→GENERATING 顺序推送阶段事件")
  void shouldEmitStagesInOrderWhenStreamingAnswer() {
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "回答");
    RespondAction action = RespondAction.ask("下一题？", "继续验证");
    List<AnswerEventSink.AnswerStage> stages = new ArrayList<>();
    List<String> deltas = new ArrayList<>();
    Consumer<String> deltaConsumer = deltas::add;
    AnswerEventSink sink = new AnswerEventSink() {
      @Override
      public void onStage(AnswerStage stage) {
        stages.add(stage);
      }

      @Override
      public Consumer<String> deltaSink() {
        return deltaConsumer;
      }
    };
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), eq(deltaConsumer)))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);

    PlannedInterview updated = service.submitAnswerStreaming(
        "candidate-1", "session-1", answer, sink
    );

    assertThat(updated).isSameAs(interview);
    InOrder order = inOrder(persistenceService);
    order.verify(persistenceService).requireCandidateSession("candidate-1", "session-1");
    order.verify(persistenceService).get("session-1");
    assertThat(stages).containsExactly(
        AnswerEventSink.AnswerStage.ASSESSING,
        AnswerEventSink.AnswerStage.GENERATING
    );
    verify(runtime).runStreaming(
        any(ReActRequest.class), any(ReActBudget.class), eq(deltaConsumer)
    );
  }

  @Test
  @DisplayName("过期轮次在调用模型前被拒绝")
  void shouldRejectStaleTurnBeforeCallingModel() {
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(2));

    assertThatThrownBy(() -> service.submitAnswer(
        "session-1",
        new CandidateAnswer(1, "过期回答")
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("轮次");

    verifyNoInteractions(runtime);
  }

  @Test
  @DisplayName("提交期间发生乐观锁冲突时提示刷新重试")
  void shouldTranslateOptimisticLockFailure() {
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "回答");
    RespondAction action = RespondAction.ask("下一题？", "继续验证");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenThrow(new OptimisticLockingFailureException("concurrent update"));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("刷新");
    verify(telemetry).stateConflict("session-1", 1);
    verify(telemetry, never()).assessmentRecorded(anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("推进当前面试时不读取候选人长期记忆")
  void shouldNotReadLongTermMemoryDuringCurrentInterview() {
    PlannedInterview interview = interviewAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "相同质量的回答");
    RespondAction action = RespondAction.ask("下一题？", "继续验证");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);

    service.submitAnswer("session-1", answer);

    verifyNoInteractions(candidateMemoryService);
    verify(telemetry).assessmentRecorded("专业基础", DepthLevel.L2, 1);
  }

  @Test
  @DisplayName("最后一轮在事务外生成练习并交给最终裁决一起落库")
  void shouldPersistPracticeRecommendationsWithFinalDecision() {
    PlannedInterview interview = interviewAtTurn(6);
    CandidateAnswer answer = new CandidateAnswer(6, "最终回答");
    PracticeRecommendation recommendation = new PracticeRecommendation(
        2,
        "系统设计",
        DepthLevel.L2,
        "question:99",
        "MEDIUM",
        "新的练习题？",
        PracticeStatus.PENDING
    );
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(practiceRecommendationService.recommend(
        eq("session-1"),
        any(),
        any()
    )).thenReturn(List.of(recommendation));
    when(persistenceService.latestAssessmentDepth("session-1", 2))
        .thenReturn(DepthLevel.L1);
    when(persistenceService.recordDecision(any(AdaptiveDecisionPersistenceInput.class)))
        .thenReturn(interview);

    service.submitAnswer("session-1", answer);

    verify(practiceRecommendationService).recommend(
        eq("session-1"),
        any(),
        any()
    );
    verify(telemetry).followUpAssessed(
        "系统设计",
        DepthLevel.L1,
        DepthLevel.L2
    );
    verifyNoInteractions(runtime);
  }

  @Test
  @DisplayName("练习检索失败时快速失败且不完成面试")
  void shouldNotAdvanceWhenPracticeRecommendationFails() {
    PlannedInterview interview = interviewAtTurn(6);
    CandidateAnswer answer = new CandidateAnswer(6, "最终回答");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(practiceRecommendationService.recommend(
        eq("session-1"),
        any(),
        any()
    )).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "练习检索失败"
    ));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessage("练习检索失败");

    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
    verifyNoInteractions(runtime);
  }

  private DepthAssessmentAgent assessmentAgent() {
    return new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
        DepthLevel.L2,
        0.8,
        "描述了实际应用",
        false,
        List.of(request.context().answer())
    ));
  }

  private AssessmentEvidenceValidator evidenceValidator() {
    return new AssessmentEvidenceValidator();
  }

  @Test
  @DisplayName("算法题最终评估包含沙箱执行结果并替换原评估")
  void shouldReassessAlgorithmAnswerWithSandboxResult() {
    AtomicReference<AssessmentRequest> captured = new AtomicReference<>();
    service = serviceWithAssessmentAgent(new DepthAssessmentAgent((request, provider) -> {
      captured.set(request);
      return new AssessmentProposal(
          DepthLevel.L3,
          0.9,
          "代码通过全部测试",
          false,
          List.of(request.context().answer())
      );
    }));
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(2));

    service.reassessAlgorithmResult(
        "session-1",
        1,
        "verdict=AC, passed=10/10, timeMs=12"
    );

    assertThat(captured.get().context().toolResult())
        .isEqualTo("verdict=AC, passed=10/10, timeMs=12");
    verify(persistenceService).replaceAssessment(
        eq("session-1"),
        eq(1),
        any(),
        anyList()
    );
  }

  @Test
  @DisplayName("工具结果事件开启独立 ReAct 运行但不推进会话轮次")
  void shouldHandleToolResultInNewRuntimeInvocation() {
    ToolResultEvent event = new ToolResultEvent(
        1,
        "sandbox_submit",
        "execution-1",
        "verdict=WA, passed=4/10",
        "verdict=WA, passed=4/10, firstFailedCase=7"
    );
    PlannedInterview interview = interviewAtTurn(1);
    RespondAction followUp = RespondAction.ask("第 7 个用例失败可能是哪类边界？", "基于判题结果追问");
    when(persistenceService.get("session-1")).thenReturn(interview);
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenReturn(ReActResult.withoutTools(followUp));
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    assertThat(service.handleToolResult("session-1", event)).contains(followUp);

    verify(runtime).runStreaming(request.capture(), any(ReActBudget.class), isNull());
    assertThat(request.getValue().interviewerContext().currentToolResult()).isEqualTo(event);
    assertThat(request.getValue().interviewerContext().workingMemory().triggerType())
        .isEqualTo(TurnTriggerType.TOOL_RESULT);
    assertThat(request.getValue().interviewerContext().workingMemory().followUpDepth())
        .isEqualTo(1);
    verify(persistenceService).completeToolResultEvent(
        "session-1",
        event,
        followUp,
        List.of()
    );
    verify(persistenceService, never())
        .recordDecision(any(AdaptiveDecisionPersistenceInput.class));
  }

  @Test
  @DisplayName("工具结果处理抛运行时异常时回滚预留并原样抛出")
  void shouldRollbackReservationWhenToolResultHandlingFailsWithRuntimeException() {
    ToolResultEvent event = new ToolResultEvent(
        1,
        "sandbox_submit",
        "execution-1",
        "verdict=WA, passed=4/10",
        "verdict=WA, passed=4/10, firstFailedCase=7"
    );
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(1));
    RuntimeException failure = new RuntimeException("database unavailable");
    when(runtime.runStreaming(any(ReActRequest.class), any(ReActBudget.class), isNull()))
        .thenThrow(failure);

    assertThatThrownBy(() -> service.handleToolResult("session-1", event))
        .isSameAs(failure);

    verify(persistenceService).discardToolResultReservation(event);
    verify(persistenceService, never()).completeToolResultEvent(
        anyString(), any(), any(), anyList()
    );
  }

  @Test
  @DisplayName("预留事件撞唯一约束时按已存在语义返回 false")
  void shouldTreatUniqueViolationAsDuplicateReservation() {
    ToolResultEvent event = new ToolResultEvent(
        1,
        "sandbox_submit",
        "execution-1",
        "verdict=WA, passed=4/10",
        "verdict=WA, passed=4/10, firstFailedCase=7"
    );
    when(persistenceService.reserveToolResultEvent("session-1", event))
        .thenThrow(new DataIntegrityViolationException("uk_agent_tool_result_event"));

    assertThat(service.reserveToolResultEvent("session-1", event)).isFalse();
  }

  private PlannedInterview interviewAtTurn(int currentTurn) {
    AdaptiveInterviewSession session = new AdaptiveInterviewSession(
        "session-1",
        AdaptiveInterviewSession.RUNTIME_VERSION,
        AdaptiveSessionStatus.IN_PROGRESS,
        currentTurn,
        6
    );
    List<AdaptiveInterviewTurn> turns = currentTurn == 1
        ? List.of(new AdaptiveInterviewTurn(
            1,
            0,
            "第一题？",
            "验证基础",
            null,
            null,
            null,
            null
        ))
        : List.of(
            new AdaptiveInterviewTurn(
                1,
                0,
                "第一题？",
                "验证基础",
                "第一轮回答",
                AgentResponseType.ASK,
                "第二题？",
                "继续"
            ),
            new AdaptiveInterviewTurn(
                2,
                0,
                "第二题？",
                "继续验证",
                null,
                null,
                null,
                null
            )
        );
    InterviewPlan plan = InterviewPlan.decide("session-1", proposal());
    for (int turn = 1; turn < currentTurn; turn++) {
      plan = plan.answer(turn);
    }
    return new PlannedInterview(
        new AdaptiveInterviewHistory(session, "candidate-1", "JD", "Resume", null, turns),
        plan,
        List.of()
    );
  }

  private PlannedInterview interviewAtLastDimensionFirstTurn() {
    InterviewPlan plan = InterviewPlan.decide("session-1", proposal());
    for (int turn = 1; turn <= 4; turn++) {
      plan = plan.answer(turn);
    }
    List<AdaptiveInterviewTurn> turns = List.of(
        answeredTurn(1, 0),
        answeredTurn(2, 0),
        answeredTurn(3, 1),
        answeredTurn(4, 1),
        new AdaptiveInterviewTurn(
            5, 2, "末维度第一题？", "验证边界", null, null, null, null
        )
    );
    return new PlannedInterview(
        new AdaptiveInterviewHistory(
            new AdaptiveInterviewSession(
                "session-1",
                AdaptiveInterviewSession.RUNTIME_VERSION,
                AdaptiveSessionStatus.IN_PROGRESS,
                5,
                6
            ),
            "candidate-1",
            "JD",
            "Resume",
            null,
            turns
        ),
        plan,
        List.of()
    );
  }

  private AdaptiveInterviewTurn answeredTurn(int turnIndex, int dimensionOrder) {
    return new AdaptiveInterviewTurn(
        turnIndex,
        dimensionOrder,
        "第" + turnIndex + "题？",
        "继续验证",
        "第" + turnIndex + "轮回答",
        AgentResponseType.ASK,
        "下一题？",
        "继续"
    );
  }

  private PlanProposal proposal() {
    return new PlanProposal(List.of(
        new DimensionProposal("专业基础", "缓存与并发", "JAVA", 2, List.of(), "java-backend"),
        new DimensionProposal("项目经验", "架构取舍", "PROJECT", 2, List.of(), "java-backend"),
        new DimensionProposal("系统设计", "扩展边界", "DISTRIBUTED", 2, List.of(), "system-design")
    ));
  }
}
