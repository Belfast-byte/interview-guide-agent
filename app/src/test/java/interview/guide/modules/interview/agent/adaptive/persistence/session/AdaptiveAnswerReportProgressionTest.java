package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerDecisionService.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.AssessmentEvidenceValidator;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import interview.guide.modules.interview.skill.InterviewSkillService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdaptiveAnswerReportProgressionTest extends AdaptiveAnswerPersistenceFixture {

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
            level, 0.9, "根据当前回答评估", level == DepthLevel.L0 ? List.of() : List.of(new SourceQuote(SourceQuote.Source.ANSWER_TEXT, answer.content(), null)))),
        new AssessmentEvidenceValidator(), mock(InterviewSkillService.class));
    AnswerAssessment assessed = assessor.assess(interview, answer);
    var decision = new AgentDecision(WorkingMemory.empty(), new AgentDecision.Finish("本场结束"));
    transactions.commit(new AdaptiveAnswerTransactionService.AnswerCommit(owner, interview,
        new AdaptiveAnswerTransactionService.CommitFacts(answer,
            new AnswerProgressionDecision(assessed, decision)), "finish"));
    assertThat(episodes.findBySessionIdAndTurnIndex(SESSION_ID, answer.turnIndex()))
        .isPresent();
    entityManager.flush();
    entityManager.clear();
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status()).isEqualTo(AdaptiveSessionStatus.COMPLETED);
    assertThat(claims.claim(SESSION_ID, owner, answer, "replay", java.time.Duration.ofMinutes(1)))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
  }

}
