package interview.guide.modules.interview.agent.adaptive.core.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveInterviewSessionTest {

  @Nested
  @DisplayName("会话状态转换")
  class StateTransitions {

    @Test
    @DisplayName("开始会话后进入第一轮")
    void shouldStartAtFirstTurn() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 6).start();

      assertThat(session.status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
      assertThat(session.currentTurn()).isEqualTo(1);
      assertThat(session.runtimeVersion()).isEqualTo("adaptive-agent-v1");
    }

    @Test
    @DisplayName("追问动作推进到下一轮")
    void shouldAdvanceWhenAskingNextQuestion() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 6).start();

      SessionTransition transition = session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.ask("下一题？", "需要继续验证")
      );

      assertThat(transition.session().currentTurn()).isEqualTo(2);
      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
      assertThat(transition.appliedAction().type()).isEqualTo(AgentResponseType.ASK);
    }

    @Test
    @DisplayName("达到结束门槛的结束动作完成会话且不增加轮次")
    void shouldCompleteWithoutAdvancingTurn() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 2).start();

      SessionTransition transition = session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.finish("面试结束。", "信息已经充分")
      );

      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
      assertThat(transition.session().currentTurn()).isEqualTo(1);
      assertThat(transition.appliedAction().content()).isEqualTo("面试结束。");
    }

    @Test
    @DisplayName("未达结束门槛的模型结束提案被拒绝")
    void shouldRejectFinishBelowTurnThreshold() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 6).start();

      assertThatThrownBy(() -> session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.finish("面试结束。", "模型想提前结束")
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("门槛");

      assertThat(session.currentTurn()).isEqualTo(1);
    }

    @Test
    @DisplayName("已回答轮次达到计划一半时接受提前结束")
    void shouldAcceptFinishAtHalfOfPlannedTurns() {
      AdaptiveInterviewSession session = new AdaptiveInterviewSession(
          "session-1",
          AdaptiveInterviewSession.RUNTIME_VERSION,
          AdaptiveSessionStatus.IN_PROGRESS,
          3,
          6
      );

      SessionTransition transition = session.apply(
          new CandidateAnswer(3, "回答"),
          RespondAction.finish("核心考察点已覆盖。", "信息已经充分")
      );

      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
      assertThat(transition.session().currentTurn()).isEqualTo(3);
    }

    @Test
    @DisplayName("达到轮次上限时由代码覆盖模型的追问建议")
    void shouldFinishAtTurnBudget() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 1).start();

      SessionTransition transition = session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.ask("模型仍想追问？", "模型建议继续")
      );

      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
      assertThat(transition.appliedAction().type()).isEqualTo(AgentResponseType.FINISH);
      assertThat(transition.appliedAction().reason()).isEqualTo("轮次预算已用尽");
    }

    @Test
    @DisplayName("过期轮次回答不能推进当前会话")
    void shouldRejectStaleTurn() {
      AdaptiveInterviewSession session = AdaptiveInterviewSession.create("session-1", 6).start();

      assertThatThrownBy(() -> session.apply(
          new CandidateAnswer(2, "过期回答"),
          RespondAction.ask("下一题？", "继续")
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("轮次");

      assertThat(session.currentTurn()).isEqualTo(1);
    }

    @Test
    @DisplayName("创建失败的会话拒绝答题")
    void shouldRejectAnswerWhenSessionFailed() {
      AdaptiveInterviewSession session = new AdaptiveInterviewSession(
          "session-1",
          AdaptiveInterviewSession.RUNTIME_VERSION,
          AdaptiveSessionStatus.FAILED,
          0,
          6
      );

      assertThatThrownBy(() -> session.assertCanAnswer(new CandidateAnswer(1, "回答")))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("创建失败");
    }
  }
}
