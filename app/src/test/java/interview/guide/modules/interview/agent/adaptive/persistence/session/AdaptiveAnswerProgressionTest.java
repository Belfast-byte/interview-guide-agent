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
    AdaptiveCreationRepositories.class,
    AdaptiveCreationTransactionService.class,
    AdaptiveAnswerCoreRepositories.class,
    AdaptiveAssessmentRepositories.class,
    AdaptiveAnswerTransactionService.class,
    AdaptiveAnswerClaimService.class,
    QuestionExposurePersistence.class,
    QuestionIdentityFactory.class
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

  @MockitoBean private AdaptiveAnswerSideEffects sideEffects;

  @Test
  @DisplayName("相同回答可重放且最终事实与下一 Turn 只提交一次")
  void shouldCommitAnswerProgressionOnce() {
    InterviewPlan plan = initializeInterview();
    MemoryOwner owner = new MemoryOwner(null, "candidate-1");
    CandidateAnswer answer = new CandidateAnswer(1, "我会使用版本号处理并发更新。");
    assertThat(claims.claim(SESSION_ID, owner, answer))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.NEW);
    assertThat(claims.claim(SESSION_ID, owner, answer))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.PENDING);

    PlannedInterview interview = interview(plan);
    var commit = new AdaptiveAnswerTransactionService.AnswerCommit(
        owner,
        interview,
        new AdaptiveAnswerTransactionService.CommitFacts(answer, progression(plan))
    );
    transactions.commit(commit);
    transactions.commit(commit);
    entityManager.flush();
    entityManager.clear();

    assertThat(claims.claim(SESSION_ID, owner, answer))
        .isEqualTo(AdaptiveAnswerClaimService.ClaimResult.COMMITTED);
    assertThat(sessions.findById(SESSION_ID).orElseThrow().status())
        .isEqualTo(AdaptiveSessionStatus.IN_PROGRESS);
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).hasSize(2);
    assertThat(assessments.findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(SESSION_ID))
        .hasSize(1);
    assertThat(gaps.findSessionGaps(SESSION_ID)).hasSize(1);
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
    claims.claim(SESSION_ID, owner, new CandidateAnswer(1, "回答 A"));

    assertThatThrownBy(() -> claims.claim(
        SESSION_ID, owner, new CandidateAnswer(1, "回答 B")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不同回答");
    assertThat(turns.findBySessionIdAndTurnIndex(SESSION_ID, 1).orElseThrow().answer())
        .isEqualTo("回答 A");
  }

  private InterviewPlan initializeInterview() {
    InterviewPlan plan = testPlan(SESSION_ID, new PlanProposal(List.of(new DimensionProposal(
        "缓存一致性", "并发更新", "CACHE", 2, "java-backend"))));
    AdaptiveSessionCreation session = new AdaptiveSessionCreation(
        null, SESSION_ID, "candidate-1", "JD", "Resume", "provider-1",
        null, null, EVALUATION_SETTINGS);
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
    return new PlannedInterview(history, plan, coverage, List.of());
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
    return new AnswerProgressionDecision(assessed, decision);
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
