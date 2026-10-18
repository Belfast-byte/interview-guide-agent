package interview.guide.modules.interview.agent.adaptive.observability;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** 输出已采用的工作记忆，供运行时调试；跨请求恢复仍读取数据库。 */
public final class WorkingMemorySnapshotLog {
  private static final Logger LOG = LoggerFactory.getLogger("interview.working-memory.snapshot");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private WorkingMemorySnapshotLog() {}

  public static void published(String sessionId, int turnIndex, WorkingMemory memory) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("工作记忆快照必须在发布事务中注册");
    }
    // 注册时冻结快照；文件 IO 放在提交后，回滚不留下已采用记录。
    String line = JSON.writeValueAsString(new Snapshot(
        1, Instant.now().toString(), "working_memory_published", "COMMITTED",
        sessionId, turnIndex, memory));
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        LOG.info("{}", line);
      }
    });
  }

  private record Snapshot(
      int schemaVersion, String timestamp, String event, String status,
      String sessionId, int turnIndex, WorkingMemory workingMemory
  ) {}
}
