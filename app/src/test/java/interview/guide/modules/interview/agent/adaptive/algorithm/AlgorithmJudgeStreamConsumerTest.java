package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.infrastructure.redis.RedisService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmJudgeStreamConsumerTest {

  @Mock
  private RedisService redisService;

  @Mock
  private AlgorithmPersistenceService persistenceService;

  @Mock
  private SandboxWorker sandboxWorker;

  @Mock
  private AlgorithmJudgeStreamProducer producer;

  private AlgorithmJudgeStreamConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new AlgorithmJudgeStreamConsumer(
        redisService,
        persistenceService,
        sandboxWorker,
        producer
    );
  }

  @Test
  @DisplayName("沙箱返回首次 IE 时重新投递同一 submissionId")
  void shouldRequeueFirstInternalError() {
    SandboxExecution execution = execution();
    AlgorithmProblem problem = problem();
    SandboxExecutionResult result = new SandboxExecutionResult(
        SandboxVerdict.IE,
        0,
        0,
        0,
        0,
        null,
        List.of()
    );
    when(persistenceService.getExecution("execution-1")).thenReturn(execution);
    when(persistenceService.getProblem("two-sum")).thenReturn(problem);
    when(sandboxWorker.execute(execution, problem)).thenReturn(result);
    when(persistenceService.applyResult("execution-1", result)).thenReturn(true);
    when(producer.sendExecution("execution-1")).thenReturn(true);

    consumer.processBusiness(new AlgorithmJudgeStreamConsumer.ExecutionTask("execution-1"));

    verify(producer).sendExecution("execution-1");
  }

  @Test
  @DisplayName("worker 调用耗尽 Stream 重试后记录 IE 待重判")
  void shouldMarkInfrastructureFailureAfterRetries() {
    AlgorithmJudgeStreamConsumer.ExecutionTask task =
        new AlgorithmJudgeStreamConsumer.ExecutionTask("execution-1");

    consumer.markFailed(task, "sandbox unavailable");

    verify(persistenceService).markInfrastructureFailure("execution-1");
  }

  private SandboxExecution execution() {
    return new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.RUNNING, null, null, null, null, null, null,
        null, false, 0
    );
  }

  private AlgorithmProblem problem() {
    return new AlgorithmProblem(
        "two-sum", "两数之和", "题干", AlgorithmDifficulty.EASY, "array,hash",
        "cases/sample.json", "cases/hidden.json", 2_000, 262_144
    );
  }
}
