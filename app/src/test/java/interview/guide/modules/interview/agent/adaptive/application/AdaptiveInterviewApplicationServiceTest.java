package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

  private AdaptiveInterviewApplicationService service;

  @BeforeEach
  void setUp() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        properties,
        telemetry,
        planningAgent
    );
  }

  @Test
  @DisplayName("创建会话时先在事务外生成首题再写入事实")
  void shouldCallModelBeforeCreatingSession() {
    when(planningAgent.propose(any(), any())).thenReturn(proposal());
    RespondAction firstQuestion = RespondAction.ask("第一题？", "验证基础");
    PlannedInterview expected = interviewAtTurn(1);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(firstQuestion);
    when(persistenceService.create(
        anyString(),
        anyString(),
        anyString(),
        any(),
        any(InterviewPlan.class),
        any(RespondAction.class)
    )).thenReturn(expected);

    PlannedInterview actual = service.create("JD", "Resume", null);

    assertThat(actual).isSameAs(expected);
    verify(telemetry).decisionSucceeded(eq(AgentResponseType.ASK), anyLong());
    InOrder order = inOrder(planningAgent, runtime, persistenceService);
    order.verify(planningAgent).propose(any(), any());
    order.verify(runtime).run(any(ReActRequest.class), any(ReActBudget.class));
    order.verify(persistenceService).create(
        anyString(),
        anyString(),
        anyString(),
        any(),
        any(InterviewPlan.class),
        any(RespondAction.class)
    );
  }

  @Test
  @DisplayName("规划失败时不调用面试官也不创建会话")
  void shouldNotCreateSessionWhenPlanningFails() {
    when(planningAgent.propose(any(), any())).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "规划失败"
    ));

    assertThatThrownBy(() -> service.create("JD", "Resume", null))
        .isInstanceOf(BusinessException.class)
        .hasMessage("规划失败");

    verifyNoInteractions(runtime, persistenceService);
  }

  @Test
  @DisplayName("非法规划被代码拒绝且不调用面试官或创建会话")
  void shouldRejectInvalidPlanBeforeCreatingSession() {
    when(planningAgent.propose(any(), any())).thenReturn(new PlanProposal(List.of()));

    assertThatThrownBy(() -> service.create("JD", "Resume", null))
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

    verify(persistenceService, never()).recordDecision(anyString(), any(), any());
    verify(telemetry).decisionFailed(eq("session-1"), eq(1), anyInt(), anyLong());
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
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class))).thenReturn(action);
    when(persistenceService.recordDecision("session-1", answer, action))
        .thenThrow(new OptimisticLockingFailureException("concurrent update"));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("刷新");
    verify(telemetry).stateConflict("session-1", 1);
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
        new AdaptiveInterviewHistory(session, "JD", "Resume", null, turns),
        plan
    );
  }

  private PlanProposal proposal() {
    return new PlanProposal(List.of(
        new DimensionProposal("专业基础", "缓存与并发", 2, List.of(), null),
        new DimensionProposal("项目经验", "架构取舍", 2, List.of(), null),
        new DimensionProposal("系统设计", "扩展边界", 2, List.of(), null)
    ));
  }
}
