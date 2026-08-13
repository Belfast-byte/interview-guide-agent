package interview.guide.modules.interview.agent.adaptive.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.DimensionBriefProposal;
import interview.guide.modules.interview.agent.adaptive.memory.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import java.util.ArrayList;
import java.util.List;
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
          default -> throw new AssertionError("最后一轮不应再调用模型");
        },
        (request, action) -> {
          throw new AssertionError("不应执行工具");
        }
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        (request, provider) -> proposal(1),
        new ContextAssembler(),
        briefService()
    );

    PlannedInterview created = service.create("JD", "Resume", null);
    PlannedInterview secondTurn = service.submitAnswer(
        created.history().session().id(),
        new CandidateAnswer(1, "第一轮完整回答")
    );
    PlannedInterview completed = service.submitAnswer(
        created.history().session().id(),
        new CandidateAnswer(2, "第二轮完整回答")
    );

    assertThat(secondTurn.history().session().currentTurn()).isEqualTo(2);
    assertThat(completed.history().session().status())
        .isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(completed.history().turns()).extracting(turn -> turn.answer())
        .containsExactly("第一轮完整回答", "第二轮完整回答");
    assertThat(completed.history().turns()).extracting(turn -> turn.questionReason())
        .containsExactly("开始考察", "继续追问");
    assertThat(completed.plan().dimensions().getFirst().status())
        .isEqualTo(PlanDimensionStatus.COMPLETED);
    assertThat(persistenceService.get(created.history().session().id()))
        .isEqualTo(completed);
  }

  @Test
  @DisplayName("模型持续追问时由代码在第六轮结束面试")
  void shouldCompleteAtSixTurnBudget() {
    AtomicInteger modelCalls = new AtomicInteger();
    List<String> questionDimensions = new ArrayList<>();
    List<Integer> visibleHistorySizes = new ArrayList<>();
    List<Integer> visibleBriefCounts = new ArrayList<>();
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> {
          questionDimensions.add(context.request().dimension());
          visibleHistorySizes.add(
              context.request().interviewerContext().currentDimensionTurns().size()
          );
          visibleBriefCounts.add(
              context.request().interviewerContext().completedDimensionBriefs().size()
          );
          return RespondAction.ask(
              "第 " + (modelCalls.incrementAndGet()) + " 题？",
              "继续考察"
          );
        },
        (request, action) -> {
          throw new AssertionError("不应执行工具");
        }
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        (request, provider) -> proposal(3),
        new ContextAssembler(),
        briefService()
    );

    PlannedInterview interview = service.create("JD", "Resume", null);
    for (int turn = 1; turn <= 6; turn++) {
      interview = service.submitAnswer(
          interview.history().session().id(),
          new CandidateAnswer(turn, "第 " + turn + " 轮完整回答")
      );
    }

    AdaptiveInterviewHistory history = interview.history();
    assertThat(history.session().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(history.session().currentTurn()).isEqualTo(6);
    assertThat(history.turns()).hasSize(6);
    assertThat(history.turns().getLast().responseType())
        .isEqualTo(AgentResponseType.FINISH);
    assertThat(history.turns().getLast().decisionReason()).isEqualTo("规划轮次已全部完成");
    assertThat(interview.plan().dimensions()).extracting(dimension -> dimension.status())
        .containsOnly(PlanDimensionStatus.COMPLETED);
    assertThat(questionDimensions).containsExactly(
        "维度-0",
        "维度-0",
        "维度-1",
        "维度-1",
        "维度-2",
        "维度-2"
    );
    assertThat(visibleHistorySizes).containsExactly(0, 1, 0, 1, 0, 1);
    assertThat(visibleBriefCounts).containsExactly(0, 0, 1, 1, 2, 2);
    assertThat(interview.dimensionBriefs()).hasSize(3);
    assertThat(interview.dimensionBriefs())
        .flatExtracting(brief -> brief.turnIndexes())
        .containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(modelCalls).hasValue(6);
  }

  private PlanProposal proposal(int dimensionCount) {
    return new PlanProposal(java.util.stream.IntStream.range(0, dimensionCount)
        .mapToObj(index -> new DimensionProposal(
            "维度-" + index,
            "重点-" + index,
            2,
            List.of(),
            null
        ))
        .toList());
  }

  private DimensionBriefService briefService() {
    return new DimensionBriefService((request, provider) -> new DimensionBriefProposal(
        "已讨论当前维度的方案与取舍",
        request.turns().stream().map(turn -> turn.turnIndex()).toList()
    ));
  }
}
