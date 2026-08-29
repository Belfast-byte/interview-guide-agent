package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testSession;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionResult;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.persistence.assessment.AdaptiveAgentAssessmentEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveTurnCreation;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaAlgorithmEvidenceConsumer.class)
class JpaAlgorithmEvidenceConsumerIntegrationTest {

  private static final int CODE_HASH_LENGTH = 64;

  @Autowired
  private JpaAlgorithmEvidenceConsumer consumer;

  @Autowired
  private EntityManager entityManager;

  private long turnId;

  @BeforeEach
  void setUpFacts() {
    entityManager.persist(new AdaptiveAgentSessionEntity(
        testSession("session-1", 4).start(),
        new AdaptiveSessionCreation(
            null, "session-1", "candidate-1", "JD", "Resume",
            null, null, null, EVALUATION_SETTINGS
        )
    ));
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        "session-1", 1, 1, RespondAction.ask("question", "reason"), TurnProvenance.initial()
    ));
    entityManager.persist(turn);
    entityManager.persist(new AdaptiveAgentAssessmentEntity(
        1,
        new AssessmentDecision(
            "session-1", 1, DepthLevel.L2, 0.8, "assessment", List.of()
        )
    ));
    entityManager.flush();
    turnId = turn.id();
  }

  @Test
  @DisplayName("有效终态原子写入唯一 Evidence 并只消费一次")
  void shouldConsumeCandidateEvidenceOnce() {
    persistExecution(doneExecution("execution-1", SandboxVerdict.WA, 1));

    assertThat(consumer.consume("execution-1")).isTrue();
    assertThat(consumer.consume("execution-1")).isFalse();

    assertThat(evidenceCount("execution-1")).isEqualTo(1);
    assertThat(execution("execution-1").consumedAt()).isNotNull();
  }

  @Test
  @DisplayName("平台失败与过期执行只标记消费而不形成候选人证据")
  void shouldConsumeNonCandidateResultsWithoutEvidence() {
    SandboxExecutionEntity infrastructureFailure = doneExecution(
        "execution-ie", SandboxVerdict.IE, 1);
    SandboxExecutionEntity superseded = doneExecution("execution-stale", SandboxVerdict.WA, 2);
    superseded.supersedeWith("execution-new");
    SandboxExecutionEntity timeout = pendingExecution("execution-timeout", 3);
    timeout.markQueuedTimeout();
    persistExecution(infrastructureFailure);
    persistExecution(superseded);
    persistExecution(timeout);

    assertThat(consumer.consume("execution-ie")).isTrue();
    assertThat(consumer.consume("execution-stale")).isTrue();
    assertThat(consumer.consume("execution-timeout")).isTrue();

    entityManager.flush();
    assertThat(totalEvidenceCount()).isZero();
    assertThat(execution("execution-ie").consumedAt()).isNotNull();
    assertThat(execution("execution-stale").consumedAt()).isNotNull();
    assertThat(execution("execution-timeout").consumedAt()).isNotNull();
  }

  private SandboxExecutionEntity doneExecution(String id, SandboxVerdict verdict, int seq) {
    SandboxExecutionEntity execution = pendingExecution(id, seq);
    execution.markRunning();
    execution.apply(new SandboxExecutionResult(
        verdict, 4, 10, 120, 32_768, 7, List.of(), null
    ));
    return execution;
  }

  private SandboxExecutionEntity pendingExecution(String id, int seq) {
    return new SandboxExecutionEntity(
        id,
        new CreateSandboxExecution(
            "session-1", 1, "two-sum", SandboxLanguage.JAVA,
            "sandbox/source/" + id + ".java",
            "a".repeat(CODE_HASH_LENGTH),
            SandboxRunMode.FULL
        ),
        turnId,
        seq
    );
  }

  private void persistExecution(SandboxExecutionEntity execution) {
    entityManager.persist(execution);
    entityManager.flush();
  }

  private SandboxExecutionEntity execution(String id) {
    entityManager.clear();
    return entityManager.find(SandboxExecutionEntity.class, id);
  }

  private long evidenceCount(String executionId) {
    return entityManager.createQuery(
        "select count(e) from AdaptiveAgentEvidenceEntity e where e.sandboxExecutionId = :id",
        Long.class
    ).setParameter("id", executionId).getSingleResult();
  }

  private long totalEvidenceCount() {
    return entityManager.createQuery(
        "select count(e) from AdaptiveAgentEvidenceEntity e",
        Long.class
    ).getSingleResult();
  }
}
