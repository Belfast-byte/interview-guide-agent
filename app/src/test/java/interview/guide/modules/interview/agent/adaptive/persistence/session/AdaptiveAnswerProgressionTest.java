package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.application.PendingAssessmentReferences;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.episode.QuestionIdentityFactory;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaAssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.skill.InterviewSkillService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaMemoryEvidenceService.class,
    tools.jackson.databind.ObjectMapper.class,
    interview.guide.modules.interview.agent.adaptive.rubric.RubricGenerationStore.class,
    interview.guide.modules.interview.agent.adaptive.persistence.session.RubricSnapshotResolver.class,
    AdaptiveCreationTransactionService.class,
    AdaptiveAssessmentRepositories.class,
    AdaptiveAnswerTransactionService.class,
    AdaptiveAnswerClaimService.class,
    QuestionExposurePersistence.class,
    QuestionIdentityFactory.class,
    JpaAssessmentReportFactsSource.class,
    AssessmentReportService.class
})
class AdaptiveAnswerProgressionTest {

  private static final String SESSION_ID = "session-1";

  @Autowired private AdaptiveCreationTransactionService creation;
  @Autowired private AdaptiveAnswerClaimService claims;
  @Autowired private AdaptiveAnswerTransactionService transactions;
  @Autowired private AdaptiveAgentSessionRepository sessions;
  @Autowired private AdaptiveAgentTurnRepository turns;
  @Autowired private AdaptiveAgentAssessmentRepository assessments;
  @Autowired private AssessmentProbeGapRepository gaps;
  @Autowired private AdaptiveAgentEvidenceRepository evidences;
  @Autowired private EntityManager entityManager;
  @Autowired private AssessmentReportService reports;
  @MockitoBean private AlgorithmEvidenceSource algorithmEvidence;

  @MockitoBean private AdaptiveAnswerSideEffects sideEffects;

  @Test
  @DisplayName("相同回答可重放且最终事实与下一 Turn 只提交一次")
  void shouldCommitAnswerProgressionOnce() {
    InterviewPlan plan = initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "我会使用版本号处理并发更新。");
    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.PENDING);

    PlannedInterview interview = interview(plan);
    var commit = new AdaptiveAnswerTransactionService.AnswerCommit(
        owner,
        interview,
        new AdaptiveAnswerTransactionService.CommitFacts(answer, progression(plan)), "execution-1"
    );
    transactions.commit(commit);
    transactions.commit(commit);
    entityManager.flush();
    entityManager.clear();

    assertThat(claims.claim(SESSION_ID, owner, answer, "execution-1", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status())
        .isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).hasSize(2);
    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(SESSION_ID))
        .hasSize(1);
    assertThat(gaps.findSessionGaps(SESSION_ID)).hasSize(1);
    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(SESSION_ID)
        .getFirst().budgetExhaustedFinal()).isTrue();
    assertThat(gaps.findSessionGaps(SESSION_ID).getFirst().closedByAssessmentId())
        .isNull();
    assertThat(evidences.findReportEvidence(SESSION_ID)).hasSize(1);
    var nextTurn = turns.findBySessionIdAndTurnIndex(SESSION_ID, 2).orElseThrow();
    assertThat(nextTurn.sourceProbeGapId()).isPositive();
    assertThat(nextTurn.workingMemory().focus().activeGapId()).isPositive();
    assertThat(nextTurn.workingMemory().deliberation().hypotheses().getFirst()
        .evidenceLinks().supportingEvidenceIds().getFirst()).isPositive();
    verify(sideEffects, times(1)).saveEpisode(any(), any(), any());
    verify(sideEffects, times(1)).saveExposure(any());
  }

  @Test
  @DisplayName("不同回答重放明确冲突且不覆盖原 answer claim")
  void shouldRejectDifferentAnswerReplay() {
    initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    claims.claim(SESSION_ID, owner, new CandidateAnswer(1, "回答 A"), "execution-1", java.time.Duration.ofMinutes(1));

    assertThatThrownBy(() -> claims.claim(
        SESSION_ID, owner, new CandidateAnswer(1, "回答 B"), "execution-2", java.time.Duration.ofMinutes(1)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不同回答");
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().answer())
        .isEqualTo("回答 A");
  }

  @Test
  @DisplayName("失败领取可重试，旧执行者不能提交或清除新租约")
  void shouldFenceFailedExecutionAfterRetry() {
    InterviewPlan plan = initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "我会使用版本号处理并发更新。");
    claims.claim(SESSION_ID, owner, answer, "old", java.time.Duration.ofMinutes(1));
    claims.fail(SESSION_ID, owner, 1, "old", "评估失败");
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow()
        .toDomain().answerStatus().name()).isEqualTo("RETRYABLE");
    assertThat(claims.claim(SESSION_ID, owner, answer, "new", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    claims.fail(SESSION_ID, owner, 1, "old", "迟到的失败");
    var turn = turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow();
    turn.requireExecution("new");
    assertThat(turn.toDomain().answerError()).isNull();
    assertThatThrownBy(() -> transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(
        owner, interview(plan), new AdaptiveAnswerTransactionService.CommitFacts(answer, progression(plan)), "old")))
        .isInstanceOf(BusinessException.class).hasMessageContaining("接管");
    assertThat(assessments.findBySessionIdAndTurnIndex(SESSION_ID, 1)).isEmpty();
  }

  @Test
  @DisplayName("租约到期后可重新领取，代码提交事实保持不变")
  void shouldRecoverExpiredCodeAnswerLease() {
    initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "class Solution {}",
        new interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission(
            "two-sum", null, "JAVA", "FULL"));
    claims.claim(SESSION_ID, owner, answer, "expired", java.time.Duration.ofSeconds(-1));
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow()
        .toDomain().answerStatus().name()).isEqualTo("RETRYABLE");
    assertThat(claims.claim(SESSION_ID, owner, answer, "replacement", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().candidateAnswer())
        .isEqualTo(answer);
  }

  @Test
  @DisplayName("L0 空证据回答落库并提前结束后，未覆盖维度仍能生成未考察报告")
  void shouldReportL0AndUnassessedTargetsAfterEarlyFinish() {
    InterviewPlan plan = initializeInterview(EVALUATION_SETTINGS, List.of(
        new DimensionProposal("缓存一致性", "并发更新", "CACHE", 2, "java-backend"),
        new DimensionProposal("数据库", "索引", "INDEX", 2, "java-backend")));
    finishWithAssessment(plan, new CandidateAnswer(1, "不知道"), DepthLevel.L0);

    var report = reports.candidateReport(SESSION_ID);
    assertThat(report.dimensions()).hasSize(2);
    assertThat(report.dimensions().getFirst().depthLevel()).isEqualTo(DepthLevel.L0);
    assertThat(report.dimensions().getFirst().confidence()).isEqualTo(0.9);
    assertThat(report.dimensions().getFirst().evidences()).isEmpty();
    assertThat(report.dimensions().get(1).depthLevel()).isNull();
    assertThat(report.dimensions().get(1).confidence()).isNull();
    assertThat(report.dimensions().get(1).rationale()).contains("未考察");
    assertThat(report.weakPoints()).singleElement().satisfies(weak ->
        assertThat(weak.dimension()).isEqualTo("缓存一致性"));
    assertThat(evidences.findReportEvidence(SESSION_ID)).isEmpty();
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).singleElement().satisfies(turn ->
        assertThat(turn.toDomain().answerStatus().name()).isEqualTo("COMPLETED"));
  }

  @ParameterizedTest
  @EnumSource(value = CandidateLevel.class, names = {"INTERN", "CAMPUS"})
  @DisplayName("实际表现超过实习或校招提问上限时，评估落库与报告保持实际等级")
  void shouldPreservePerformanceAboveQuestionCeiling(CandidateLevel level) {
    var settings = new InterviewSessionSettings(SessionMode.EVALUATION, level, PracticeScope.none());
    InterviewPlan plan = initializeInterview(settings, List.of(
        new DimensionProposal("缓存一致性", "并发更新", "CACHE", 2, "java-backend")));
    DepthLevel demonstrated = level == CandidateLevel.INTERN ? DepthLevel.L3 : DepthLevel.L4;
    assertThat(demonstrated).isGreaterThan(plan.dimension(0).depthCeiling());
    finishWithAssessment(plan, new CandidateAnswer(1, "比较方案代价并说明适用边界"), demonstrated);

    assertThat(assessments.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().depthLevel())
        .isEqualTo(demonstrated);
    assertThat(reports.candidateReport(SESSION_ID).dimensions()).singleElement().satisfies(conclusion -> {
      assertThat(conclusion.depthLevel()).isEqualTo(demonstrated);
      assertThat(conclusion.evidences()).singleElement().satisfies(evidence ->
          assertThat(evidence.quote()).isEqualTo("比较方案代价并说明适用边界"));
    });
  }

  private void finishWithAssessment(InterviewPlan plan, CandidateAnswer answer, DepthLevel level) {
    var owner = new MemoryOwner(null, "candidate-1");
    claims.claim(SESSION_ID, owner, answer, "finish", java.time.Duration.ofMinutes(1));
    PlannedInterview interview = interview(plan);
    var assessor = new AdaptiveAnswerAssessmentService(
        new DepthAssessmentAgent((request, provider) -> new AssessmentProposal(
            level, 0.9, "根据当前回答评估", level == DepthLevel.L0 ? List.of() : List.of(answer.content()))),
        new AssessmentEvidenceValidator(), mock(InterviewSkillService.class));
    AnswerAssessment assessed = assessor.assess(interview, answer);
    var decision = new AgentDecision(WorkingMemory.empty(), new AgentDecision.Finish("本场结束"));
    transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(owner, interview,
        new AdaptiveAnswerTransactionService.CommitFacts(answer,
            new AnswerProgressionDecision(assessed, decision, false)), "finish"));
    entityManager.flush();
    entityManager.clear();
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(claims.claim(SESSION_ID, owner, answer, "replay", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
  }

  private InterviewPlan initializeInterview() {
    return initializeInterview(EVALUATION_SETTINGS, List.of(new DimensionProposal(
        "缓存一致性", "并发更新", "CACHE", 2, "java-backend")));
  }

  private InterviewPlan initializeInterview(InterviewSessionSettings settings, List<DimensionProposal> dimensions) {
    InterviewPlan plan = InterviewPlan.decide(SESSION_ID, new PlanProposal(dimensions), settings);
    AdaptiveSessionCreation session = new AdaptiveSessionCreation(
        null, SESSION_ID, "candidate-1", "JD", "Resume", "provider-1",
        null, null, settings);
    creation.initialize(session, plan);
    creation.publishFirstTurn(new AdaptiveCreationTransactionService.InitialTurnCommit(
        SESSION_ID, plan, initialDecision()));
    return plan;
  }

  private PlannedInterview interview(InterviewPlan plan) {
    var session = sessions.findById(SESSION_ID).orElseThrow();
    var history = new AdaptiveInterviewHistory(
        session.toDomain(), "candidate-1", "JD", "Resume", "provider-1",
        null, null,
        turns.findBySessionIdOrderByTurnIndex(SESSION_ID).stream()
            .map(AdaptiveAgentTurnEntity::toDomain).toList(),
        null
    );
    var coverage = CoverageProjector.project(new CoverageFacts(
        plan.maxTurns(),
        plan.dimensions().stream().map(dimension -> dimension.target()).toList(),
        List.of(new CoverageFacts.TurnFact(1, "target-0")),
        List.of(),
        List.of(),
        List.of()
    ));
    return new PlannedInterview(history, plan, coverage);
  }

  private AnswerProgressionDecision progression(InterviewPlan plan) {
    AssessmentDecision assessment = new AssessmentDecision(
        SESSION_ID,
        1,
        DepthLevel.L2,
        0.8,
        "理解版本冲突",
        List.of("使用版本号"),
        List.of(new ProbeGap("版本号", "缺少推进规则"))
    );
    AnswerAssessment assessed = new AnswerAssessment(
        plan.dimension(0),
        assessment,
        List.of(new ValidatedAssessmentEvidence(EvidenceType.QUOTE, "使用版本号", null))
    );
    WorkingMemory memory = new WorkingMemory(
        1,
        new WorkingMemory.Focus(
            "target-0",
            PendingAssessmentReferences.gapId(0),
            List.of()
        ),
        new WorkingMemory.Deliberation(
            List.of(new WorkingMemory.Hypothesis(
                "候选人理解乐观并发",
                "OPEN",
                new WorkingMemory.EvidenceLinks(
                    List.of(PendingAssessmentReferences.evidenceId(0)), List.of())
            )),
            "验证版本推进",
            List.of()
        )
    );
    AgentDecision decision = new AgentDecision(memory, new AgentDecision.Ask(
        "target-0",
        PendingAssessmentReferences.gapId(0),
        new AgentDecision.QuestionDraft("版本号如何推进？", "验证冲突细节", List.of())
    ));
    return new AnswerProgressionDecision(assessed, decision, true);
  }

  private AgentDecision initialDecision() {
    return new AgentDecision(
        WorkingMemory.empty(),
        new AgentDecision.Ask(
            "target-0",
            null,
            new AgentDecision.QuestionDraft("如何处理缓存并发更新？", "验证基础", List.of())
        )
    );
  }
}
