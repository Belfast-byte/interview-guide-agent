package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testCreation;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptMemoryService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimsProposal;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefProposal;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaWorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.JdbcAbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningAgent;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    AdaptiveInterviewPersistenceService.class,
    AbilityProfileSnapshotService.class,
    CandidateMemoryService.class,
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class,
    JpaWorkingMemoryFactSource.class
})
class AdaptiveInterviewFlowIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Autowired
  private CandidateMemoryService candidateMemoryService;

  @Autowired
  private WorkingMemoryFactSource workingMemoryFactSource;

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
        },
        new DeadlineExecutor()
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        (request, provider) -> proposal(1),
        new ContextAssembler(),
        workingMemoryFactSource,
        briefService(),
        episodePromptMemoryService(),
        mock(PlanningTaxonomy.class),
        claimService(),
        assessmentAgent(),
        evidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class),
        mock(AlgorithmInterviewTelemetry.class),
        mock(CodeAnalysisInterviewContextService.class),
        mock(InterviewSkillService.class),
        mock(CandidateLlmProviderService.class),
        task -> task.run(),
        syncAnswerExecutor()
    );

    PlannedInterview created = service.createForTenant(testCreation("candidate-1"));
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
    List<Integer> visibleEpisodeCounts = new ArrayList<>();
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> {
          questionDimensions.add(
              context.request().interviewerContext().targetDimension()
          );
          visibleHistorySizes.add(
              context.request().interviewerContext().currentDimensionTurns().size()
          );
          visibleEpisodeCounts.add(
              context.request().interviewerContext().episodeHistory().size()
          );
          return RespondAction.ask(
              "第 " + (modelCalls.incrementAndGet()) + " 题？",
              "继续考察"
          );
        },
        (request, action) -> {
          throw new AssertionError("不应执行工具");
        },
        new DeadlineExecutor()
    );
    AdaptiveInterviewApplicationService service = new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        (request, provider) -> proposal(3),
        new ContextAssembler(),
        workingMemoryFactSource,
        briefService(),
        episodePromptMemoryService(),
        mock(PlanningTaxonomy.class),
        claimService(),
        assessmentAgent(),
        evidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class),
        mock(AlgorithmInterviewTelemetry.class),
        mock(CodeAnalysisInterviewContextService.class),
        mock(InterviewSkillService.class),
        mock(CandidateLlmProviderService.class),
        task -> task.run(),
        syncAnswerExecutor()
    );

    PlannedInterview interview = service.createForTenant(testCreation("candidate-1"));
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
    assertThat(visibleEpisodeCounts).containsOnly(0);
    assertThat(interview.dimensionBriefs()).hasSize(3);
    assertThat(interview.dimensionBriefs())
        .flatExtracting(brief -> brief.turnIndexes())
        .containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(modelCalls).hasValue(6);
  }

  @Test
  @DisplayName("创建立即返回 CREATED 空骨架，后台完成后进入 IN_PROGRESS 并带首题")
  void shouldExposeCreatedSkeletonThenFirstTurn() {
    AdaptiveInterviewApplicationService lazyService = service(
        (request, provider) -> proposal(1),
        task -> {
        }
    );

    PlannedInterview skeleton = lazyService.createForTenant(testCreation("candidate-async"));

    assertThat(skeleton.history().session().status()).isEqualTo(AdaptiveSessionStatus.CREATED);
    assertThat(skeleton.history().turns()).isEmpty();
    assertThat(skeleton.plan().dimensions()).isEmpty();

    AdaptiveInterviewApplicationService syncService = service(
        (request, provider) -> proposal(1),
        task -> task.run()
    );
    PlannedInterview returned = syncService.createForTenant(testCreation("candidate-sync"));
    assertThat(returned.history().session().status()).isEqualTo(AdaptiveSessionStatus.CREATED);

    PlannedInterview created = persistenceService.get(returned.history().session().id());
    assertThat(created.history().session().status()).isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
    assertThat(created.history().session().maxTurns()).isEqualTo(2);
    assertThat(created.history().turns()).hasSize(1);
    assertThat(created.history().turns().getFirst().question()).isEqualTo("第一题？");
  }

  @Test
  @DisplayName("创建链路失败时会话置为 FAILED 并记录可读原因")
  void shouldMarkSessionFailedWhenCreationFails() {
    AdaptiveInterviewApplicationService failingService = service(
        (request, provider) -> {
          throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "规划模型不可用");
        },
        task -> task.run()
    );

    PlannedInterview skeleton = failingService.createForTenant(testCreation("candidate-fail"));

    assertThat(skeleton.history().session().status()).isEqualTo(AdaptiveSessionStatus.CREATED);
    PlannedInterview failed = persistenceService.get(skeleton.history().session().id());
    assertThat(failed.history().session().status()).isEqualTo(AdaptiveSessionStatus.FAILED);
    assertThat(failed.history().failureReason()).isEqualTo("规划模型不可用");
    assertThat(failed.history().turns()).isEmpty();
  }

  private AdaptiveInterviewApplicationService service(
      PlanningAgent planningAgent,
      AdaptiveInterviewCreationTaskRunner creationExecutor
  ) {
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> RespondAction.ask("第一题？", "开始考察"),
        (request, action) -> {
          throw new AssertionError("不应执行工具");
        },
        new DeadlineExecutor()
    );
    return new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        planningAgent,
        new ContextAssembler(),
        workingMemoryFactSource,
        briefService(),
        episodePromptMemoryService(),
        mock(PlanningTaxonomy.class),
        claimService(),
        assessmentAgent(),
        evidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class),
        mock(AlgorithmInterviewTelemetry.class),
        mock(CodeAnalysisInterviewContextService.class),
        mock(InterviewSkillService.class),
        mock(CandidateLlmProviderService.class),
        creationExecutor,
        syncAnswerExecutor()
    );
  }

  /** 测试用答题执行器：异步任务同步执行，保持事实链断言时序确定。 */
  private AdaptiveInterviewAnswerExecutor syncAnswerExecutor() {
    AdaptiveInterviewAnswerExecutor executor = mock(AdaptiveInterviewAnswerExecutor.class);
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any(Runnable.class));
    return executor;
  }

  private EpisodePromptMemoryService episodePromptMemoryService() {
    EpisodePromptMemoryService service = mock(EpisodePromptMemoryService.class);
    doAnswer(invocation -> List.of()).when(service).select(any(), any());
    return service;
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
    return new AssessmentEvidenceValidator();
  }
}
