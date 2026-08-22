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
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.CandidateClaimType;
import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.inOrder;
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

  private AdaptiveInterviewApplicationService service;

  @BeforeEach
  void setUp() {
    service = serviceWithAssessmentAgent(assessmentAgent());
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
        dimensionBriefService,
        candidateMemoryService,
        planningTaxonomy,
        candidateClaimExtractionService,
        assessmentAgent,
        evidenceValidator(),
        practiceRecommendationService,
        algorithmAssessmentEvidenceService,
        algorithmTelemetry,
        codeAnalysisContextService,
        skillService
    );
  }

  @Test
  @DisplayName("创建会话时先在事务外生成首题再写入事实")
  void shouldCallModelBeforeCreatingSession() {
    CoveredTopic coveredTopic = new CoveredTopic("java-backend", "REDIS");
    UnverifiedClaim unverifiedClaim = new UnverifiedClaim(
        CandidateClaimType.PROJECT_EXPERIENCE,
        "java-backend",
        "PROJECT"
    );
    PlanningSkill planningSkill = new PlanningSkill(
        "java-backend",
        List.of("JAVA", "REDIS", "PROJECT")
    );
    when(candidateMemoryService.coveredTopics("candidate-1"))
        .thenReturn(List.of(coveredTopic));
    when(candidateMemoryService.unverifiedClaims("candidate-1"))
        .thenReturn(List.of(unverifiedClaim));
    when(planningTaxonomy.catalog()).thenReturn(List.of(planningSkill));
    when(planningAgent.propose(any(), any())).thenReturn(proposal());
    RespondAction firstQuestion = RespondAction.ask("第一题？", "验证基础");
    PlannedInterview expected = interviewAtTurn(1);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(firstQuestion));
    when(persistenceService.create(
        any(),
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(),
        any(InterviewPlan.class),
        any(RespondAction.class),
        anyList()
    )).thenReturn(expected);

    PlannedInterview actual = service.create("candidate-1", "JD", "Resume", null);

    assertThat(actual).isSameAs(expected);
    ArgumentCaptor<PlanningRequest> planningRequest = ArgumentCaptor.forClass(
        PlanningRequest.class
    );
    verify(planningAgent).propose(planningRequest.capture(), any());
    assertThat(planningRequest.getValue().context().coveredTopics())
        .containsExactly(coveredTopic);
    assertThat(planningRequest.getValue().context().skillCatalog())
        .containsExactly(planningSkill);
    assertThat(planningRequest.getValue().context().unverifiedClaims())
        .containsExactly(unverifiedClaim);
    verify(planningTaxonomy).validate(any(InterviewPlan.class));
    verify(telemetry).decisionSucceeded(eq(AgentResponseType.ASK), anyLong());
    InOrder order = inOrder(planningAgent, runtime, persistenceService);
    order.verify(planningAgent).propose(any(), any());
    order.verify(runtime).run(any(ReActRequest.class), any(ReActBudget.class));
    order.verify(persistenceService).create(
        any(),
        anyString(),
        anyString(),
        anyString(),
        anyString(),
        any(),
        any(InterviewPlan.class),
        any(RespondAction.class),
        anyList()
    );
  }

  @Test
  @DisplayName("规划失败时不调用面试官也不创建会话")
  void shouldNotCreateSessionWhenPlanningFails() {
    when(planningAgent.propose(any(), any())).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "规划失败"
    ));

    assertThatThrownBy(() -> service.create("candidate-1", "JD", "Resume", null))
        .isInstanceOf(BusinessException.class)
        .hasMessage("规划失败");

    verifyNoInteractions(runtime, persistenceService);
  }

  @Test
  @DisplayName("非法规划被代码拒绝且不调用面试官或创建会话")
  void shouldRejectInvalidPlanBeforeCreatingSession() {
    when(planningAgent.propose(any(), any())).thenReturn(new PlanProposal(List.of()));

    assertThatThrownBy(() -> service.create("candidate-1", "JD", "Resume", null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("1 到 12");

    verify(telemetry).planRejected(anyString(), anyInt());
    verifyNoInteractions(runtime, persistenceService);
  }

  @Test
  @DisplayName("模型失败时不写入回答和下一题")
  void shouldNotAdvanceWhenModelFails() {
    when(persistenceService.get("session-1")).thenReturn(interviewAtTurn(1));
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型失败"));

    assertThatThrownBy(() -> service.submitAnswer(
        "session-1",
        new CandidateAnswer(1, "回答")
    )).isInstanceOf(BusinessException.class)
        .hasMessage("模型失败");

    verify(persistenceService, never()).recordDecision(
        anyString(),
        any(),
        any(),
        anyList(),
        any(),
        anyList(),
        any(),
        anyList(),
        anyList()
    );
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of()),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    )).thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    gapService.submitAnswer("session-1", answer);

    verify(runtime).run(request.capture(), any(ReActBudget.class));
    assertThat(request.getValue().interviewerContext().currentAnswerGaps())
        .containsExactly(gap);
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(action));
    when(dimensionBriefService.summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    )).thenReturn(null);
    when(candidateClaimExtractionService.extract(
        eq("session-1"), any(), anyList(), eq(answer), anyList(), nullable(String.class)
    )).thenReturn(List.of());
    when(persistenceService.latestAssessmentDepth("session-1", 0))
        .thenReturn(DepthLevel.L1);
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of()),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    )).thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    gapService.submitAnswer("session-1", answer);

    verify(runtime).run(request.capture(), any(ReActBudget.class));
    assertThat(request.getValue().interviewerContext().currentAnswerGaps()).isEmpty();
    assertThat(request.getValue().interviewerContext().targetDimension()).isEqualTo("项目经验");
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
    when(candidateClaimExtractionService.extract(
        eq("session-1"), any(), anyList(), eq(answer), anyList(), nullable(String.class)
    )).thenReturn(List.of());
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of()),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    )).thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    l4Service.submitAnswer("session-1", answer);

    verify(runtime).run(request.capture(), any(ReActBudget.class));
    assertThat(request.getValue().interviewerContext().targetDimension())
        .isEqualTo("项目经验");
    assertThat(request.getValue().interviewerContext().currentAnswerGaps()).isEmpty();
    verify(dimensionBriefService).summarize(
        eq("session-1"), any(), anyList(), eq(answer), nullable(String.class)
    );
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(new ReActResult(action, List.of(pending)));
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of(pending)),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    )).thenReturn(interview);
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    service.submitAnswer("session-1", answer);

    verify(runtime).run(request.capture(), any(ReActBudget.class));
    assertThat(request.getValue().interviewerContext().currentCodeSubmission())
        .isEqualTo(answer);
    verify(persistenceService).recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of(pending)),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    );
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(RespondAction.ask("下一题？", "继续")));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("异步判题任务");

    verify(persistenceService, never()).recordDecision(
        anyString(), any(), any(), anyList(), any(), anyList(), any(), anyList(), anyList()
    );
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
    verify(persistenceService, never()).recordDecision(
        anyString(),
        any(),
        any(),
        anyList(),
        any(),
        anyList(),
        any(),
        anyList(),
        anyList()
    );
  }

  @Test
  @DisplayName("维度小结失败时不调用下一维度面试官也不推进状态")
  void shouldNotAdvanceWhenDimensionBriefFails() {
    PlannedInterview interview = interviewAtTurn(2);
    CandidateAnswer answer = new CandidateAnswer(2, "第二轮回答");
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
    verify(persistenceService, never()).recordDecision(
        anyString(),
        any(),
        any(),
        anyList(),
        any(),
        anyList(),
        any(),
        anyList(),
        anyList()
    );
  }

  @Test
  @DisplayName("声明抽取失败时不调用下一维度面试官也不推进状态")
  void shouldNotAdvanceWhenClaimExtractionFails() {
    PlannedInterview interview = interviewAtTurn(2);
    CandidateAnswer answer = new CandidateAnswer(2, "第二轮回答");
    when(persistenceService.get("session-1")).thenReturn(interview);
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
    verify(persistenceService, never()).recordDecision(
        anyString(),
        any(),
        any(),
        anyList(),
        any(),
        anyList(),
        any(),
        anyList(),
        anyList()
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of()),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    ))
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(action));
    when(persistenceService.recordDecision(
        eq("session-1"), eq(answer), eq(action), eq(List.of()),
        isNull(), eq(List.of()), any(), anyList(), eq(List.of())
    )).thenReturn(interview);

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
    when(persistenceService.recordDecision(
        eq("session-1"),
        eq(answer),
        any(),
        eq(List.of()),
        isNull(),
        eq(List.of()),
        any(),
        anyList(),
        eq(List.of(recommendation))
    )).thenReturn(interview);

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

    verify(persistenceService, never()).recordDecision(
        anyString(),
        any(),
        any(),
        anyList(),
        any(),
        anyList(),
        any(),
        anyList(),
        anyList()
    );
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
    when(persistenceService.reserveToolResultEvent("session-1", event)).thenReturn(true);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(ReActResult.withoutTools(followUp));
    ArgumentCaptor<ReActRequest> request = ArgumentCaptor.forClass(ReActRequest.class);

    assertThat(service.handleToolResult("session-1", event)).contains(followUp);

    verify(runtime).run(request.capture(), any(ReActBudget.class));
    assertThat(request.getValue().interviewerContext().currentToolResult()).isEqualTo(event);
    verify(persistenceService).completeToolResultEvent(
        "session-1",
        event,
        followUp,
        List.of()
    );
    verify(persistenceService, never()).recordDecision(
        anyString(), any(), any(), anyList(), any(), anyList(), any(), anyList(), anyList()
    );
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

  private PlanProposal proposal() {
    return new PlanProposal(List.of(
        new DimensionProposal("专业基础", "缓存与并发", "JAVA", 2, List.of(), "java-backend"),
        new DimensionProposal("项目经验", "架构取舍", "PROJECT", 2, List.of(), "java-backend"),
        new DimensionProposal("系统设计", "扩展边界", "DISTRIBUTED", 2, List.of(), "system-design")
    ));
  }
}
