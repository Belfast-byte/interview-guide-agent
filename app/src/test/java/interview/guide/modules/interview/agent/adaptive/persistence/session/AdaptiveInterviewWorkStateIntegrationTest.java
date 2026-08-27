package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkPhase;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateMemoryService;
import interview.guide.modules.interview.agent.adaptive.persistence.algorithm.JpaAlgorithmEvidenceSource;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.JpaAssessmentReportFactsSource;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AbilityProfileSnapshotService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationDependencies;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.AssessmentReconciliationService;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeAssessmentCorrectionPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.EpisodeFactPersistence;
import interview.guide.modules.interview.agent.adaptive.persistence.memory.JdbcAbilityCounterIncrementStore;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStateJsonCodec;
import interview.guide.modules.interview.agent.adaptive.persistence.working.WorkStatePersistenceService;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
    AdaptiveInterviewPersistenceService.class,
    WorkStatePersistenceService.class,
    WorkStateJsonCodec.class,
    AbilityProfileSnapshotService.class,
    EpisodeFactPersistence.class,
    JdbcAbilityCounterIncrementStore.class,
    EpisodeAssessmentCorrectionPersistence.class,
    AssessmentReconciliationDependencies.class,
    AssessmentReconciliationService.class,
    CandidateMemoryService.class,
    JpaAlgorithmEvidenceSource.class,
    JpaAssessmentReportFactsSource.class,
    AssessmentReportService.class,
    AdaptiveInterviewWorkStateIntegrationTest.JacksonTestConfig.class
})
class AdaptiveInterviewWorkStateIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Test
  @DisplayName("计划、首题和 WorkState 在创建完成事务中一起可见")
  void shouldInitializeWorkStateWithFirstTurn() {
    String sessionId = "session-work-state";
    persistenceService.createSkeleton(new AdaptiveSessionCreation(
        null, sessionId, "candidate-1", "JD", "Resume", "provider",
        "Provider", "model", EVALUATION_SETTINGS));
    InterviewPlan plan = testPlan(sessionId, new PlanProposal(List.of(
        new DimensionProposal(
            "缓存", "Redis 持久化", "REDIS", 2, List.of(), "java-backend")
    )));

    var interview = persistenceService.completeCreation(
        sessionId, plan, RespondAction.ask("RDB 和 AOF 如何取舍？", "验证持久化"), List.of());
    InterviewWorkState state = interview.workState();

    assertThat(state.revision()).isEqualTo(1);
    assertThat(state.phase()).isEqualTo(WorkPhase.AWAITING_ANSWER);
    assertThat(state.awaitingAnswerTurnIndex()).isEqualTo(1);
    assertThat(state.activeTarget().remainingBudget().turns()).isEqualTo(1);
    assertThat(interview.history().turns()).hasSize(1);
  }

  @TestConfiguration
  static class JacksonTestConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
