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
import static org.mockito.ArgumentMatchers.anyString;
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

  private AdaptiveInterviewApplicationService service;

  @BeforeEach
  void setUp() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    service = new AdaptiveInterviewApplicationService(persistenceService, runtime, properties);
  }

  @Test
  @DisplayName("创建会话时先在事务外生成首题再写入事实")
  void shouldCallModelBeforeCreatingSession() {
    RespondAction firstQuestion = RespondAction.ask("第一题？", "验证基础");
    AdaptiveInterviewHistory expected = historyAtTurn(1);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenReturn(firstQuestion);
    when(persistenceService.create(
        anyString(),
        anyString(),
        anyString(),
        any(),
        anyInt(),
        anyString()
    )).thenReturn(expected);

    AdaptiveInterviewHistory actual = service.create("JD", "Resume", null);

    assertThat(actual).isSameAs(expected);
    InOrder order = inOrder(runtime, persistenceService);
    order.verify(runtime).run(any(ReActRequest.class), any(ReActBudget.class));
    order.verify(persistenceService).create(
        anyString(),
        anyString(),
        anyString(),
        any(),
        anyInt(),
        anyString()
    );
  }

  @Test
  @DisplayName("模型失败时不写入回答和下一题")
  void shouldNotAdvanceWhenModelFails() {
    AdaptiveInterviewHistory history = historyAtTurn(1);
    when(persistenceService.get("session-1")).thenReturn(history);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型失败"));

    assertThatThrownBy(() -> service.submitAnswer(
        "session-1",
        new CandidateAnswer(1, "回答")
    )).isInstanceOf(BusinessException.class)
        .hasMessage("模型失败");

    verify(persistenceService, never()).recordDecision(anyString(), any(), any());
  }

  @Test
  @DisplayName("过期轮次在调用模型前被拒绝")
  void shouldRejectStaleTurnBeforeCallingModel() {
    when(persistenceService.get("session-1")).thenReturn(historyAtTurn(2));

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
    AdaptiveInterviewHistory history = historyAtTurn(1);
    CandidateAnswer answer = new CandidateAnswer(1, "回答");
    RespondAction action = RespondAction.ask("下一题？", "继续验证");
    when(persistenceService.get("session-1")).thenReturn(history);
    when(runtime.run(any(ReActRequest.class), any(ReActBudget.class))).thenReturn(action);
    when(persistenceService.recordDecision("session-1", answer, action))
        .thenThrow(new OptimisticLockingFailureException("concurrent update"));

    assertThatThrownBy(() -> service.submitAnswer("session-1", answer))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("刷新");
  }

  private AdaptiveInterviewHistory historyAtTurn(int currentTurn) {
    AdaptiveInterviewSession session = new AdaptiveInterviewSession(
        "session-1",
        AdaptiveInterviewSession.RUNTIME_VERSION,
        AdaptiveSessionStatus.IN_PROGRESS,
        currentTurn,
        6
    );
    List<AdaptiveInterviewTurn> turns = currentTurn == 1
        ? List.of(new AdaptiveInterviewTurn(1, "第一题？", null, null, null, null))
        : List.of(
            new AdaptiveInterviewTurn(
                1,
                "第一题？",
                "第一轮回答",
                AgentResponseType.ASK,
                "第二题？",
                "继续"
            ),
            new AdaptiveInterviewTurn(2, "第二题？", null, null, null, null)
        );
    return new AdaptiveInterviewHistory(session, "JD", "Resume", null, turns);
  }
}
