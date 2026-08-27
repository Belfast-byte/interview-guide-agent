package interview.guide.modules.interview.agent.adaptive.core.session;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testSession;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveInterviewSessionTest {

  @Test
  @DisplayName("正式评估拒绝练习范围，练习模式必须指定主题")
  void shouldKeepModeAndPracticeScopeConsistent() {
    PracticeScope scope = new PracticeScope(List.of(new TopicKey("java-backend", "REDIS")));

    assertThatThrownBy(() -> new InterviewSessionSettings(
        SessionMode.EVALUATION, CandidateLevel.CAMPUS, scope
    )).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new InterviewSessionSettings(
        SessionMode.PRACTICE, CandidateLevel.CAMPUS, PracticeScope.none()
    )).isInstanceOf(BusinessException.class);
  }

  @Nested
  @DisplayName("会话状态转换")
  class StateTransitions {

    @Test
    @DisplayName("开始会话后进入第一轮")
    void shouldStartAtFirstTurn() {
      AdaptiveInterviewSession session = testSession("session-1", 6).start();

      assertThat(session.status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
      assertThat(session.currentTurn()).isEqualTo(1);
      assertThat(session.runtimeVersion()).isEqualTo("adaptive-agent-v1");
    }

    @Test
    @DisplayName("追问动作推进到下一轮")
    void shouldAdvanceWhenAskingNextQuestion() {
      AdaptiveInterviewSession session = testSession("session-1", 6).start();

      SessionTransition transition = session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.ask("下一题？", "需要继续验证")
      );

      assertThat(transition.session().currentTurn()).isEqualTo(2);
      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
      assertThat(transition.appliedAction().type()).isEqualTo(AgentResponseType.ASK);
    }

    @Test
    @DisplayName("本地策略结束动作完成会话且不增加轮次")
    void shouldCompleteWithoutAdvancingTurn() {
      AdaptiveInterviewSession session = testSession("session-1", 2).start();

      SessionTransition transition = session.apply(
          new CandidateAnswer(1, "回答"),
          RespondAction.finish("面试结束。", "信息已经充分")
      );

      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
      assertThat(transition.session().currentTurn()).isEqualTo(1);
      assertThat(transition.appliedAction().content()).isEqualTo("面试结束。");
    }

    @Test
    @DisplayName("本地策略可以在预算未耗尽时结束")
    void shouldAcceptPolicyFinishBeforeBudgetExhaustion() {
      AdaptiveInterviewSession session = new AdaptiveInterviewSession(
          "session-1",
          AdaptiveInterviewSession.RUNTIME_VERSION,
          AdaptiveSessionStatus.IN_PROGRESS,
          3,
          6,
          EVALUATION_SETTINGS
      );

      SessionTransition transition = session.apply(
          new CandidateAnswer(3, "回答"),
          RespondAction.finish("核心考察点已覆盖。", "信息已经充分")
      );

      assertThat(transition.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
      assertThat(transition.session().currentTurn()).isEqualTo(3);
    }

    @Test
    @DisplayName("过期轮次回答不能推进当前会话")
    void shouldRejectStaleTurn() {
      AdaptiveInterviewSession session = testSession("session-1", 6).start();

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
          6,
          EVALUATION_SETTINGS
      );

      assertThatThrownBy(() -> session.assertCanAnswer(new CandidateAnswer(1, "回答")))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("创建失败");
    }
  }
}
