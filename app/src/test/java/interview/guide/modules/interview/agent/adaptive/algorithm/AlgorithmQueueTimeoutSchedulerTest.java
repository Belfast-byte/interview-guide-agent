package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmQueueTimeoutSchedulerTest {

  @Mock
  private AlgorithmPersistenceService persistenceService;

  @Mock
  private AlgorithmResultReadyHandler resultReadyHandler;

  @Test
  @DisplayName("超过 90 秒的排队任务降级并触发结果事件")
  void shouldPublishQueuedTimeout() {
    AlgorithmInterviewProperties properties = new AlgorithmInterviewProperties();
    SandboxExecution execution = new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.TIMEOUT_QUEUED, null, null, null, null, null, null,
        null, false, 0
    );
    when(persistenceService.timeoutQueuedBefore(any())).thenReturn(List.of(execution));
    AlgorithmQueueTimeoutScheduler scheduler = new AlgorithmQueueTimeoutScheduler(
        persistenceService,
        resultReadyHandler,
        properties
    );

    scheduler.degradeQueuedExecutions();

    verify(resultReadyHandler).handle(execution);
  }
}
