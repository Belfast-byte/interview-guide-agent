package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
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

  @Mock
  private AlgorithmResultReadyHandler resultReadyHandler;

  @Mock
  private AlgorithmInterviewTelemetry telemetry;

  private AlgorithmJudgeStreamConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new AlgorithmJudgeStreamConsumer(
        redisService,
        persistenceService,
        sandboxWorker,
        producer,
        resultReadyHandler,
        telemetry,
        new AlgorithmInterviewProperties()
    );
  }

  @Test
  @DisplayName("沙箱返回首次 IE 时重新投递同一 submissionId")
  void shouldRequeueFirstInternalError() {
    SandboxExecution execution = execution();
    AlgorithmProblem problem = problem();
    SandboxExecutionSpec spec = new SandboxExecutionSpec(
        "two-sum",
        "cases/hidden.json",
        null,
        2_000,
        262_144
    );
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
    when(sandboxWorker.execute(execution, spec)).thenReturn(result);
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
    SandboxExecution execution = execution();
    when(persistenceService.markInfrastructureFailure("execution-1"))
        .thenReturn(execution);

    consumer.markFailed(task, "sandbox unavailable");

    verify(persistenceService).markInfrastructureFailure("execution-1");
    verify(resultReadyHandler).handle(execution);
  }

  @Test
  @DisplayName("PATCH 工作负载直接使用场景快照和测试且不读取算法题")
  void shouldBuildPatchExecutionSpecWithoutAlgorithmProblem() {
    SandboxExecution execution = new SandboxExecution(
        "execution-2",
        "session-1",
        11L,
        2,
        SandboxWorkloadType.PATCH,
        null,
        "scenario-1",
        "repos/one.zip",
        "tests/one.zip",
        SandboxLanguage.JAVA,
        "patches/one.patch",
        "b".repeat(64),
        SandboxRunMode.FULL,
        SandboxExecutionStatus.RUNNING,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        0,
        LocalDateTime.now(),
        null,
        null
    );
    SandboxExecutionSpec spec = new SandboxExecutionSpec(
        "scenario-1",
        "tests/one.zip",
        "repos/one.zip",
        10_000,
        512 * 1024
    );
    SandboxExecutionResult result = new SandboxExecutionResult(
        SandboxVerdict.AC,
        3,
        3,
        100,
        1024,
        null,
        List.of()
    );
    when(persistenceService.getExecution("execution-2")).thenReturn(execution);
    when(sandboxWorker.execute(execution, spec)).thenReturn(result);
    when(persistenceService.applyResult("execution-2", result)).thenReturn(false);
    when(persistenceService.getExecution("execution-2")).thenReturn(execution);

    consumer.processBusiness(new AlgorithmJudgeStreamConsumer.ExecutionTask("execution-2"));

    verify(persistenceService, never()).getProblem(any());
    verify(resultReadyHandler).handle(execution);
  }

  private SandboxExecution execution() {
    return new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.RUNNING, null, null, null, null, null, null,
        null, false, 0, LocalDateTime.now().minusSeconds(1), LocalDateTime.now()
    );
  }

  private AlgorithmProblem problem() {
    return new AlgorithmProblem(
        "two-sum", "两数之和", "题干", AlgorithmDifficulty.EASY, "array,hash",
        "cases/sample.json", "cases/hidden.json", 2_000, 262_144
    );
  }
}
