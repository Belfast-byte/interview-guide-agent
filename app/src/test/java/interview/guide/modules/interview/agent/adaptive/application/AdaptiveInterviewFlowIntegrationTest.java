package interview.guide.modules.interview.agent.adaptive.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.assessment.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateClaimsProposal;
import interview.guide.modules.interview.agent.adaptive.memory.DimensionBriefProposal;
import interview.guide.modules.interview.agent.adaptive.memory.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
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
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AdaptiveInterviewPersistenceService.class, CandidateMemoryService.class})
class AdaptiveInterviewFlowIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Autowired
  private CandidateMemoryService candidateMemoryService;

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
        briefService(),
        candidateMemoryService,
        mock(PlanningTaxonomy.class),
        claimService(),
        assessmentAgent(),
        evidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class)
    );

    PlannedInterview created = service.create("candidate-1", "JD", "Resume", null);
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
    assertThat(candidateMemoryService.coveredTopics("candidate-1"))
        .containsExactly(new CoveredTopic("java-backend", "FOCUS_0"));
    assertThat(candidateMemoryService.coveredTopics("candidate-2")).isEmpty();
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
        briefService(),
        candidateMemoryService,
        mock(PlanningTaxonomy.class),
        claimService(),
        assessmentAgent(),
        evidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class)
    );

    PlannedInterview interview = service.create("candidate-1", "JD", "Resume", null);
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
            "FOCUS_" + index,
            2,
            List.of(),
            "java-backend"
        ))
        .toList());
  }

  private DimensionBriefService briefService() {
    return new DimensionBriefService((request, provider) -> new DimensionBriefProposal(
        "已讨论当前维度的方案与取舍",
        request.turns().stream().map(turn -> turn.turnIndex()).toList()
    ));
  }

  private CandidateClaimExtractionService claimService() {
    return new CandidateClaimExtractionService(
        (request, provider) -> new CandidateClaimsProposal(List.of())
    );
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
    return new AssessmentEvidenceValidator((sessionId, turnIndex, resultIds) -> {
      throw new AssertionError("纯文本引用不应查询工具结果");
    });
  }
}
