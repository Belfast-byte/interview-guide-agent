package interview.guide.modules.interview.agent.adaptive.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class WorkingMemorySnapshotLogTest {
  private static final String LOGGER_NAME = "interview.working-memory.snapshot";
  private final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final JsonMapper json = new JsonMapper();

  @BeforeEach
  void capture() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void cleanup() {
    logger.detachAppender(appender);
    appender.stop();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void completeSnapshotAppearsOnlyAfterCommitAsOneJsonLine() {
    begin();
    var memory = memory();
    WorkingMemorySnapshotLog.published("session-1", 2, memory);
    assertThat(appender.list).isEmpty();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    assertThat(appender.list).hasSize(1);
    String line = appender.list.getFirst().getFormattedMessage();
    assertThat(line).doesNotContain("\n");
    var snapshot = json.readTree(line);
    assertThat(snapshot.path("sessionId").asText()).isEqualTo("session-1");
    assertThat(snapshot.path("turnIndex").asInt()).isEqualTo(2);
    assertThat(snapshot.path("status").asText()).isEqualTo("COMMITTED");
    assertThat(snapshot.path("event").asText()).isEqualTo("working_memory_published");
    assertThat(snapshot.path("timestamp").asText()).endsWith("Z");
    assertThat(json.treeToValue(snapshot.path("workingMemory"), WorkingMemory.class))
        .isEqualTo(memory);
  }

  @Test
  void rollbackDoesNotOutputAdoptedSnapshot() {
    begin();
    WorkingMemorySnapshotLog.published("rolled-back", 2, memory());
    TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void synchronizationWithoutTransactionCannotClaimCommittedSnapshot() {
    TransactionSynchronizationManager.initSynchronization();
    assertThatThrownBy(() -> WorkingMemorySnapshotLog.published("session", 1, memory()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("发布事务");
    assertThat(appender.list).isEmpty();
  }

  @Test
  void configuredFilePreservesUtf8AndAppendsWithoutPropagation(@TempDir Path directory)
      throws Exception {
    begin();
    WorkingMemorySnapshotLog.published("session-1", 2, memory());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    String line = appender.list.getFirst().getFormattedMessage();
    Path file = directory.resolve("working-memory.jsonl");
    Files.writeString(file, line + "\n", StandardCharsets.UTF_8);

    var context = new LoggerContext();
    try {
      context.setMDCAdapter(new LogbackMDCAdapter());
      context.putProperty("WORKING_MEMORY_LOG_DIR", directory.toString());
      context.putProperty("ASSESSMENT_OBSERVATION_LOG_DIR", directory.toString());
      context.putProperty("LOG_FILE", directory.resolve("application.log").toString());
      var configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
      context.start();
      var fileLogger = context.getLogger(LOGGER_NAME);
      assertThat(fileLogger.isAdditive()).isFalse();
      fileLogger.info("{}", line);

      assertThat(context.getStatusManager().getCopyOfStatusList())
          .noneMatch(status -> status.getEffectiveLevel() == Status.ERROR);
      assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).containsExactly(line, line);
      assertThat(Files.readString(directory.resolve("application.log")))
          .doesNotContain("候选人", "session-1");
    } finally {
      context.stop();
    }
  }

  private void begin() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
  }

  private WorkingMemory memory() {
    return new WorkingMemory(1,
        new WorkingMemory.Focus("target-0", 42L,
            List.of(new WorkingMemory.GapPriority(42L, "继续验证"))),
        new WorkingMemory.Deliberation(List.of(new WorkingMemory.Hypothesis(
            "候选人理解 \"CAS\"\n需要验证边界", "OPEN",
            new WorkingMemory.EvidenceLinks(List.of(84L), List.of(85L)))),
            "换场景追问", List.of("episode:9", "rubric:7")));
  }
}
