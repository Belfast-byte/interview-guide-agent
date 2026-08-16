package interview.guide.modules.interview.agent.adaptive.persistence.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmResultReadyHandler;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionResult;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaAlgorithmResultReadyDeliveryStore.class)
@Transactional
class JpaAlgorithmResultReadyDeliveryStoreIntegrationTest {

  @Autowired
  private JpaAlgorithmResultReadyDeliveryStore store;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("终态且已过稳定期、会话进行中、无唤醒事件的执行需要补偿重投")
  void shouldFindSettledUndeliveredExecution() {
    persistSession("session-1", AdaptiveSessionStatus.IN_PROGRESS);
    String doneId = persistDoneExecution("session-1", "execution-done");
    String timeoutId = persistQueuedTimeoutExecution("session-1", "execution-timeout");

    List<String> undelivered = store.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        LocalDateTime.now().plusSeconds(1)
    );

    assertThat(undelivered).containsExactlyInAnyOrder(doneId, timeoutId);
  }

  @Test
  @DisplayName("已有唤醒事件的执行不会重复补偿")
  void shouldSkipAlreadyDeliveredExecution() {
    persistSession("session-1", AdaptiveSessionStatus.IN_PROGRESS);
    String doneId = persistDoneExecution("session-1", "execution-done");
    insertToolResultEvent("session-1", "execution-done");

    List<String> undelivered = store.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        LocalDateTime.now().plusSeconds(1)
    );

    assertThat(undelivered).doesNotContain(doneId);
  }

  @Test
  @DisplayName("已结束会话中的执行不进入补偿重投")
  void shouldSkipExecutionsOfCompletedSession() {
    persistSession("session-1", AdaptiveSessionStatus.COMPLETED);
    persistDoneExecution("session-1", "execution-done");

    List<String> undelivered = store.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        LocalDateTime.now().plusSeconds(1)
    );

    assertThat(undelivered).isEmpty();
  }

  @Test
  @DisplayName("过期提交不进入补偿重投")
  void shouldSkipSupersededExecution() {
    persistSession("session-1", AdaptiveSessionStatus.IN_PROGRESS);
    SandboxExecutionEntity execution = doneExecution("session-1", "execution-stale");
    execution.supersedeWith("execution-newer");
    persistExecution(execution);
    backdateFinishedAt("execution-stale");

    List<String> undelivered = store.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        LocalDateTime.now().plusSeconds(1)
    );

    assertThat(undelivered).isEmpty();
  }

  @Test
  @DisplayName("仍在稳定期内的执行不会被提前补偿")
  void shouldSkipExecutionWithinGracePeriod() {
    persistSession("session-1", AdaptiveSessionStatus.IN_PROGRESS);
    SandboxExecutionEntity fresh = doneExecution("session-1", "execution-fresh");
    persistExecution(fresh);

    List<String> undelivered = store.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        LocalDateTime.now().minusSeconds(60)
    );

    assertThat(undelivered).doesNotContain("execution-fresh");
  }

  private void persistSession(String sessionId, AdaptiveSessionStatus status) {
    AdaptiveInterviewSession session = status == AdaptiveSessionStatus.IN_PROGRESS
        ? AdaptiveInterviewSession.create(sessionId, 4).start()
        : new AdaptiveInterviewSession(
            sessionId,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            status,
            1,
            4
        );
    entityManager.persist(new AdaptiveAgentSessionEntity(
        session,
        null,
        "candidate-1",
        "JD",
        "Resume",
        null
    ));
  }

  private String persistDoneExecution(String sessionId, String id) {
    SandboxExecutionEntity execution = doneExecution(sessionId, id);
    persistExecution(execution);
    backdateFinishedAt(id);
    return id;
  }

  private SandboxExecutionEntity doneExecution(String sessionId, String id) {
    SandboxExecutionEntity execution = new SandboxExecutionEntity(
        id,
        new CreateSandboxExecution(
            sessionId,
            1,
            "two-sum",
            SandboxLanguage.JAVA,
            "sandbox/source/" + id + ".java",
            "a".repeat(64),
            SandboxRunMode.FULL
        ),
        10L,
        1
    );
    execution.markRunning();
    execution.apply(new SandboxExecutionResult(
        SandboxVerdict.AC,
        3,
        3,
        100,
        1024,
        null,
        List.of()
    ));
    return execution;
  }

  private String persistQueuedTimeoutExecution(String sessionId, String id) {
    SandboxExecutionEntity execution = new SandboxExecutionEntity(
        id,
        new CreateSandboxExecution(
            sessionId,
            1,
            "two-sum",
            SandboxLanguage.JAVA,
            "sandbox/source/" + id + ".java",
            "a".repeat(64),
            SandboxRunMode.FULL
        ),
        10L,
        1
    );
    execution.markQueuedTimeout();
    persistExecution(execution);
    backdateFinishedAt(id);
    return id;
  }

  private void persistExecution(SandboxExecutionEntity execution) {
    entityManager.persist(execution);
    entityManager.flush();
  }

  private void backdateFinishedAt(String id) {
    entityManager.createNativeQuery(
        "update sandbox_executions set finished_at = :finishedAt where id = :id"
    )
        .setParameter("finishedAt", LocalDateTime.now().minusMinutes(5))
        .setParameter("id", id)
        .executeUpdate();
    entityManager.flush();
  }

  private void insertToolResultEvent(String sessionId, String executionId) {
    entityManager.createNativeQuery("""
        insert into agent_tool_result_events
          (session_id, turn_index, tool_name, result_id, result_summary, result_output, status, created_at)
        values (:sessionId, 1, :toolName, :resultId, 'summary', 'output', 'RECEIVED', :createdAt)
        """)
        .setParameter("sessionId", sessionId)
        .setParameter("toolName", AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME)
        .setParameter("resultId", executionId)
        .setParameter("createdAt", LocalDateTime.now())
        .executeUpdate();
    entityManager.flush();
  }
}
