package interview.guide.modules.interview.agent.adaptive.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdaptiveInterviewPersistenceService.class)
class AdaptiveInterviewFlowIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Test
  @DisplayName("两轮 Agent 面试从首题到结束的事实链完整可重读")
  void shouldCompleteTwoTurnInterview() {
    AtomicInteger modelCalls = new AtomicInteger();
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> switch (modelCalls.getAndIncrement()) {
          case 0 -> RespondAction.ask("第一题？", "开始考察");
          case 1 -> RespondAction.ask("第二题？", "继续追问");
          default -> RespondAction.finish("面试结束。", "信息已充分");
        },
        action -> "M0 不执行工具"
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AdaptiveAgentProperties(),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry())
    );

    AdaptiveInterviewHistory created = service.create("JD", "Resume", null);
    AdaptiveInterviewHistory secondTurn = service.submitAnswer(
        created.session().id(),
        new CandidateAnswer(1, "第一轮完整回答")
    );
    AdaptiveInterviewHistory completed = service.submitAnswer(
        created.session().id(),
        new CandidateAnswer(2, "第二轮完整回答")
    );

    assertThat(secondTurn.session().currentTurn()).isEqualTo(2);
    assertThat(completed.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(completed.turns()).extracting(turn -> turn.answer())
        .containsExactly("第一轮完整回答", "第二轮完整回答");
    assertThat(completed.turns()).extracting(turn -> turn.questionReason())
        .containsExactly("开始考察", "继续追问");
    assertThat(persistenceService.get(created.session().id())).isEqualTo(completed);
  }

  @Test
  @DisplayName("模型持续追问时由代码在第六轮结束面试")
  void shouldCompleteAtSixTurnBudget() {
    AtomicInteger modelCalls = new AtomicInteger();
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> RespondAction.ask(
            "第 " + (modelCalls.incrementAndGet()) + " 题？",
            "继续考察"
        ),
        action -> "M0 不执行工具"
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AdaptiveAgentProperties(),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry())
    );

    AdaptiveInterviewHistory history = service.create("JD", "Resume", null);
    for (int turn = 1; turn <= 6; turn++) {
      history = service.submitAnswer(
          history.session().id(),
          new CandidateAnswer(turn, "第 " + turn + " 轮完整回答")
      );
    }

    assertThat(history.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(history.session().currentTurn()).isEqualTo(6);
    assertThat(history.turns()).hasSize(6);
    assertThat(history.turns().getLast().responseType())
        .isEqualTo(AgentResponseType.FINISH);
    assertThat(history.turns().getLast().decisionReason()).isEqualTo("轮次预算已用尽");
    assertThat(modelCalls).hasValue(7);
  }
}
