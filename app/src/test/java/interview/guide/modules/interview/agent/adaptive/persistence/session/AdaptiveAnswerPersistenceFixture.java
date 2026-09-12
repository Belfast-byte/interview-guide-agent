package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageFacts;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageProjector;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.memory.episode.exposure.QuestionExposurePersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentEvidenceRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AssessmentProbeGapRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaAssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    tools.jackson.databind.ObjectMapper.class,
    interview.guide.modules.interview.agent.adaptive.rubric.RubricGenerationStore.class,
    interview.guide.modules.interview.agent.adaptive.persistence.session.RubricSnapshotResolver.class,
    AdaptiveCreationTransactionService.class,
    AdaptiveAssessmentRepositories.class,
    AdaptiveAnswerTransactionService.class,
    AdaptiveAnswerClaimService.class,
    QuestionExposurePersistence.class,
    AdaptiveAnswerSideEffects.class,
    interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactPersistence.class,
    JpaAssessmentReportFactsSource.class,
    AssessmentReportService.class
})
abstract class AdaptiveAnswerPersistenceFixture {

  static final String SESSION_ID = "session-1";

  @Autowired AdaptiveCreationTransactionService creation;
  @Autowired AdaptiveAnswerClaimService claims;
  @Autowired AdaptiveAnswerTransactionService transactions;
  @Autowired AdaptiveAgentSessionRepository sessions;
  @Autowired AdaptiveAgentTurnRepository turns;
  @Autowired AdaptiveAgentAssessmentRepository assessments;
  @Autowired AssessmentProbeGapRepository gaps;
  @Autowired AdaptiveAgentEvidenceRepository evidences;
  @Autowired EntityManager entityManager;
  @Autowired AssessmentReportService reports;
  @MockitoBean AlgorithmEvidenceSource algorithmEvidence;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AdaptiveAnswerSideEffects sideEffects;
  @Autowired interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFactRepository episodes;

  InterviewPlan initializeInterview() {
    return initializeInterview(EVALUATION_SETTINGS, List.of(new DimensionProposal(
        "缓存一致性", "并发更新", "CACHE", 2, "java-backend")));
  }

  InterviewPlan initializeInterview(InterviewSessionSettings settings, List<DimensionProposal> dimensions) {
    InterviewPlan plan = InterviewPlan.decide(SESSION_ID, new PlanProposal(dimensions), settings);
    AdaptiveSessionCreation session = new AdaptiveSessionCreation(
        null, SESSION_ID, "candidate-1", "JD", "Resume", "provider-1",
        null, null, settings);
    creation.initialize(session, plan);
    creation.publishFirstTurn(new AdaptiveCreationTransactionService.InitialTurnCommit(
        SESSION_ID, plan, initialDecision()));
    return plan;
  }

  PlannedInterview interview(InterviewPlan plan) {
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

  private AgentDecision initialDecision() {
    return new AgentDecision(
        WorkingMemory.empty(),
        new AgentDecision.Ask(
            "target-0",
            null,
            new AgentDecision.QuestionDraft("如何处理缓存并发更新？", "验证基础", List.of(), QuestionType.TEXT, null, null)
        )
    );
  }
}
