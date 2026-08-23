package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentEvidenceService;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposalGenerator;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.practice.PracticeRecommendationService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisInterviewContextService;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import interview.guide.modules.interview.agent.adaptive.memory.ContextAssembler;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefService;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaimExtractionService;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodePromptMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.memory.working.WorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaWorkingMemoryFactSource;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningTaxonomy;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.BoundedReActRuntime;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.llmprovider.service.CandidateLlmProviderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    AdaptiveInterviewPersistenceService.class,
    AbilityProfileSnapshotService.class,
    EpisodeFactPersistence.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class,
    CandidateMemoryService.class,
    JpaWorkingMemoryFactSource.class
})
class WorkingMemoryApplicationIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Autowired
  private CandidateMemoryService candidateMemoryService;

  @Autowired
  private WorkingMemoryFactSource workingMemoryFactSource;

  @Autowired
  private AdaptiveAgentAssessmentRepository assessmentRepository;

  @Autowired
  private AssessmentProbeGapRepository gapRepository;

  @Test
  @DisplayName("正常下一题过滤已用 PG 并把多级 snapshot 传给 Interviewer")
  void shouldUseCurrentGapAfterFilteringUsedSource() {
    List<WorkingMemorySnapshot> snapshots = new ArrayList<>();
    AdaptiveInterviewApplicationService service = service(
        (request, provider) -> proposal(request.context().answer(), true),
        snapshots
    );
    PlannedInterview created = create(service, "candidate-chain");

    service.submitAnswer(created.history().session().id(), new CandidateAnswer(1, "回答一"));
    PlannedInterview thirdTurn = service.submitAnswer(
        created.history().session().id(),
        new CandidateAnswer(2, "回答二")
    );

    long secondAssessmentId = assessmentRepository.findBySessionIdAndTurnIndex(
        created.history().session().id(),
        2
    ).orElseThrow().id();
    long secondGapId = gapRepository
        .findByAssessmentIdOrderByGapOrderAscIdAsc(secondAssessmentId)
        .getFirst()
        .id();
    var third = thirdTurn.history().turns().get(2);
    assertThat(snapshots).extracting(WorkingMemorySnapshot::triggerType)
        .containsExactly(
            TurnTriggerType.PLANNED,
            TurnTriggerType.ASSESSMENT_GAP,
            TurnTriggerType.ASSESSMENT_GAP
        );
    assertThat(snapshots).extracting(WorkingMemorySnapshot::followUpDepth)
        .containsExactly(0, 1, 2);
    assertThat(snapshots.get(2).selectedGap().anchor()).isEqualTo("回答二");
    assertThat(third.provenance().trigger().sourceAssessmentId())
        .isEqualTo(secondAssessmentId);
    assertThat(third.provenance().trigger().sourceProbeGapId())
        .isEqualTo(secondGapId);
  }

  @Test
  @DisplayName("Tool 覆盖 provenance 后恢复未使用 PG 且保留原 Assessment 来源")
  void shouldRecoverGapAfterToolResultReplacesQuestion() {
    AtomicInteger assessments = new AtomicInteger();
    List<WorkingMemorySnapshot> snapshots = new ArrayList<>();
    AdaptiveInterviewApplicationService service = service(
        (request, provider) -> proposal(
            request.context().answer(),
            assessments.getAndIncrement() == 0
        ),
        snapshots
    );
    PlannedInterview created = create(service, "candidate-recovery");
    String sessionId = created.history().session().id();
    service.submitAnswer(sessionId, new CandidateAnswer(1, "回答一"));
    long firstAssessmentId = assessmentRepository
        .findBySessionIdAndTurnIndex(sessionId, 1)
        .orElseThrow()
        .id();
    long firstGapId = gapRepository
        .findByAssessmentIdOrderByGapOrderAscIdAsc(firstAssessmentId)
        .getFirst()
        .id();
    ToolResultEvent event = new ToolResultEvent(
        1,
        "sandbox_submit",
        "execution-recovery",
        "verdict=WA",
        "verdict=WA, passed=4/10"
    );

    assertThat(service.reserveToolResultEvent(sessionId, event)).isTrue();
    service.handleToolResult(sessionId, event);
    PlannedInterview recovered = service.submitAnswer(
        sessionId,
        new CandidateAnswer(2, "回答二")
    );

    WorkingMemorySnapshot toolSnapshot = snapshots.get(2);
    WorkingMemorySnapshot recoveredSnapshot = snapshots.get(3);
    assertThat(toolSnapshot.triggerType()).isEqualTo(TurnTriggerType.TOOL_RESULT);
    assertThat(toolSnapshot.followUpDepth()).isEqualTo(1);
    assertThat(recoveredSnapshot.selectedGap().anchor()).isEqualTo("回答一");
    assertThat(recovered.history().turns().get(2).provenance().trigger().sourceAssessmentId())
        .isEqualTo(firstAssessmentId);
    assertThat(recovered.history().turns().get(2).provenance().trigger().sourceProbeGapId())
        .isEqualTo(firstGapId);
  }

  private PlannedInterview create(
      AdaptiveInterviewApplicationService service,
      String candidateId
  ) {
    PlannedInterview skeleton = service.create(candidateId, "JD", "Resume", null);
    return persistenceService.get(skeleton.history().session().id());
  }

  private AdaptiveInterviewApplicationService service(
      AssessmentProposalGenerator assessmentGenerator,
      List<WorkingMemorySnapshot> snapshots
  ) {
    BoundedReActRuntime runtime = new BoundedReActRuntime(
        context -> {
          snapshots.add(context.request().interviewerContext().workingMemory());
          return RespondAction.ask("问题-" + snapshots.size(), "继续考察");
        },
        (request, action) -> {
          throw new AssertionError("不应执行工具");
        },
        new DeadlineExecutor()
    );
    EpisodePromptMemoryService episodeMemory = mock(EpisodePromptMemoryService.class);
    when(episodeMemory.select(any(), any())).thenReturn(List.of());
    return new AdaptiveInterviewApplicationService(
        persistenceService,
        runtime,
        new AgentRoleRegistry(new AdaptiveAgentProperties()),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        (request, provider) -> plan(),
        new ContextAssembler(),
        workingMemoryFactSource,
        mock(DimensionBriefService.class),
        candidateMemoryService,
        episodeMemory,
        mock(PlanningTaxonomy.class),
        mock(CandidateClaimExtractionService.class),
        new DepthAssessmentAgent(assessmentGenerator),
        new AssessmentEvidenceValidator(),
        mock(PracticeRecommendationService.class),
        mock(AlgorithmAssessmentEvidenceService.class),
        mock(AlgorithmInterviewTelemetry.class),
        mock(CodeAnalysisInterviewContextService.class),
        mock(InterviewSkillService.class),
        mock(CandidateLlmProviderService.class),
        task -> task.run(),
        synchronousAnswerExecutor()
    );
  }

  private AdaptiveInterviewAnswerExecutor synchronousAnswerExecutor() {
    AdaptiveInterviewAnswerExecutor executor = mock(AdaptiveInterviewAnswerExecutor.class);
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any(Runnable.class));
    return executor;
  }

  private AssessmentProposal proposal(String answer, boolean withGap) {
    List<ProbeGap> gaps = withGap
        ? List.of(new ProbeGap(answer, "未说明失败边界"))
        : List.of();
    return new AssessmentProposal(
        DepthLevel.L2,
        0.8,
        "描述了实际应用",
        false,
        List.of(answer),
        gaps
    );
  }

  private PlanProposal plan() {
    return new PlanProposal(List.of(
        new DimensionProposal("专业基础", "缓存", "cache", 3, List.of(), "java-backend"),
        new DimensionProposal("系统设计", "扩展", "scale", 1, List.of(), "system-design")
    ));
  }
}
