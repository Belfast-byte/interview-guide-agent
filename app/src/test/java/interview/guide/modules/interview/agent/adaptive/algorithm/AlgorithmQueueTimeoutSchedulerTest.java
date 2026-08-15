package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
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

  @Mock
  private AlgorithmInterviewTelemetry telemetry;

  @Test
  @DisplayName("超过 90 秒的排队任务降级并触发结果事件")
  void shouldPublishQueuedTimeout() {
    AlgorithmInterviewProperties properties = new AlgorithmInterviewProperties();
    SandboxExecution execution = new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.TIMEOUT_QUEUED, null, null, null, null, null, null,
        null, false, 0, LocalDateTime.now().minusMinutes(2), LocalDateTime.now()
    );
    when(persistenceService.timeoutQueuedBefore(any())).thenReturn(List.of(execution));
    AlgorithmQueueTimeoutScheduler scheduler = new AlgorithmQueueTimeoutScheduler(
        persistenceService,
        resultReadyHandler,
        properties,
        telemetry
    );

    scheduler.degradeQueuedExecutions();

    verify(resultReadyHandler).handle(execution);
  }
}
