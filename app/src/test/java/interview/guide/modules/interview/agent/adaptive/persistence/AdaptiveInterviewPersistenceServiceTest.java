package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdaptiveInterviewPersistenceService.class)
class AdaptiveInterviewPersistenceServiceTest {

  @Autowired
  private AdaptiveInterviewPersistenceService service;

  @Test
  @DisplayName("回答原文、决策摘要和下一题在同一事实历史中完整保存")
  void shouldPersistFullTurnAndNextQuestion() {
    String answer = "候选人的完整回答。".repeat(2000);
    service.create("session-1", "JD", "Resume", 6, "第一题？");

    AdaptiveInterviewHistory history = service.recordDecision(
        "session-1",
        new CandidateAnswer(1, answer),
        RespondAction.ask("第二题？", "需要验证边界条件")
    );

    assertThat(history.session().currentTurn()).isEqualTo(2);
    assertThat(history.turns()).hasSize(2);
    assertThat(history.turns().getFirst().answer()).isEqualTo(answer);
    assertThat(history.turns().getFirst().responseType()).isEqualTo(AgentResponseType.ASK);
    assertThat(history.turns().getFirst().decisionReason()).isEqualTo("需要验证边界条件");
    assertThat(history.turns().get(1).question()).isEqualTo("第二题？");
  }

  @Test
  @DisplayName("轮次预算覆盖模型建议后只保存结束裁决")
  void shouldPersistBudgetDecision() {
    service.create("session-2", "JD", "Resume", 1, "唯一一题？");

    AdaptiveInterviewHistory history = service.recordDecision(
        "session-2",
        new CandidateAnswer(1, "回答"),
        RespondAction.ask("不应出现的下一题？", "模型希望继续")
    );

    assertThat(history.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(history.turns()).hasSize(1);
    assertThat(history.turns().getFirst().responseType()).isEqualTo(AgentResponseType.FINISH);
    assertThat(history.turns().getFirst().decisionReason()).isEqualTo("轮次预算已用尽");
  }

  @Test
  @DisplayName("过期回答失败时不写入轮次事实")
  void shouldNotPersistStaleAnswer() {
    service.create("session-3", "JD", "Resume", 6, "第一题？");

    assertThatThrownBy(() -> service.recordDecision(
        "session-3",
        new CandidateAnswer(2, "错误轮次的回答"),
        RespondAction.ask("下一题？", "继续")
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("轮次");

    AdaptiveInterviewHistory history = service.get("session-3");
    assertThat(history.session().currentTurn()).isEqualTo(1);
    assertThat(history.turns().getFirst().answer()).isNull();
  }
}
