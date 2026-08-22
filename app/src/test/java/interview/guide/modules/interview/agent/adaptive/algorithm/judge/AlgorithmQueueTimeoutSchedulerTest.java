package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

  @Mock
  private AlgorithmResultReadyDeliveryStore resultReadyDeliveryStore;

  private AlgorithmQueueTimeoutScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new AlgorithmQueueTimeoutScheduler(
        persistenceService,
        resultReadyHandler,
        new AlgorithmInterviewProperties(),
        telemetry,
        resultReadyDeliveryStore
    );
  }

  @Nested
  @DisplayName("排队超时降级")
  class QueuedTimeout {

    @Test
    @DisplayName("超过 90 秒的排队任务降级并触发结果事件")
    void shouldPublishQueuedTimeout() {
      SandboxExecution execution = execution("execution-1", SandboxExecutionStatus.TIMEOUT_QUEUED);
      when(persistenceService.timeoutQueuedBefore(any())).thenReturn(List.of(execution));

      scheduler.degradeQueuedExecutions();

      verify(resultReadyHandler).handle(execution);
    }

    @Test
    @DisplayName("本批中单个唤醒失败不影响其余任务继续降级")
    void shouldContinueBatchWhenSingleHandleFails() {
      SandboxExecution first = execution("execution-1", SandboxExecutionStatus.TIMEOUT_QUEUED);
      SandboxExecution second = execution("execution-2", SandboxExecutionStatus.TIMEOUT_QUEUED);
      when(persistenceService.timeoutQueuedBefore(any())).thenReturn(List.of(first, second));
      doThrow(new IllegalStateException("唤醒失败")).doNothing().when(resultReadyHandler)
          .handle(any());

      assertThatCode(scheduler::degradeQueuedExecutions).doesNotThrowAnyException();

      verify(resultReadyHandler).handle(first);
      verify(resultReadyHandler).handle(second);
      verify(telemetry).resultReadyFailed();
    }
  }

  @Nested
  @DisplayName("RUNNING 卡死回收")
  class StuckRunningTimeout {

    @Test
    @DisplayName("RUNNING 超龄执行被回收为降级并唤醒编排器")
    void shouldRecycleStuckRunningAndNotify() {
      SandboxExecution execution = execution("execution-1", SandboxExecutionStatus.TIMEOUT_QUEUED);
      when(persistenceService.timeoutRunningBefore(any())).thenReturn(List.of(execution));

      scheduler.degradeStuckRunningExecutions();

      verify(telemetry).stuckRunningTimedOut();
      verify(telemetry).degraded();
      verify(resultReadyHandler).handle(execution);
    }

    @Test
    @DisplayName("单个卡死执行唤醒失败不影响本批其余执行")
    void shouldContinueStuckRunningBatchWhenHandleFails() {
      SandboxExecution first = execution("execution-1", SandboxExecutionStatus.TIMEOUT_QUEUED);
      SandboxExecution second = execution("execution-2", SandboxExecutionStatus.TIMEOUT_QUEUED);
      when(persistenceService.timeoutRunningBefore(any())).thenReturn(List.of(first, second));
      doThrow(new IllegalStateException("唤醒失败")).doNothing().when(resultReadyHandler)
          .handle(any());

      assertThatCode(scheduler::degradeStuckRunningExecutions).doesNotThrowAnyException();

      verify(resultReadyHandler).handle(first);
      verify(resultReadyHandler).handle(second);
      verify(telemetry, org.mockito.Mockito.times(2)).stuckRunningTimedOut();
      verify(telemetry).resultReadyFailed();
    }
  }

  @Nested
  @DisplayName("唤醒未送达补偿")
  class Redelivery {

    @Test
    @DisplayName("判题已落库但唤醒未送达的执行被补偿重投")
    void shouldRedeliverUndeliveredResultReadyEvent() {
      SandboxExecution execution = execution("execution-1", SandboxExecutionStatus.DONE);
      when(resultReadyDeliveryStore.findUndeliveredBefore(
          eq(AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME),
          any()
      )).thenReturn(List.of("execution-1"));
      when(persistenceService.getExecution("execution-1")).thenReturn(execution);

      scheduler.redeliverMissingResultReadyEvents();

      verify(telemetry).resultReadyRedelivered();
      verify(resultReadyHandler).handle(execution);
    }

    @Test
    @DisplayName("已送达的执行不会被补偿重复唤醒")
    void shouldSkipAlreadyDeliveredExecution() {
      when(resultReadyDeliveryStore.findUndeliveredBefore(
          eq(AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME),
          any()
      )).thenReturn(List.of());

      scheduler.redeliverMissingResultReadyEvents();

      verify(resultReadyHandler, never()).handle(any());
    }

    @Test
    @DisplayName("补偿批次中单个重投失败不影响本批其余执行")
    void shouldContinueRedeliveryBatchWhenSingleFails() {
      SandboxExecution first = execution("execution-1", SandboxExecutionStatus.DONE);
      SandboxExecution second = execution("execution-2", SandboxExecutionStatus.DONE);
      when(resultReadyDeliveryStore.findUndeliveredBefore(
          eq(AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME),
          any()
      )).thenReturn(List.of("execution-1", "execution-2"));
      when(persistenceService.getExecution("execution-1")).thenReturn(first);
      when(persistenceService.getExecution("execution-2")).thenReturn(second);
      doThrow(new IllegalStateException("唤醒失败")).doNothing().when(resultReadyHandler)
          .handle(any());

      assertThatCode(scheduler::redeliverMissingResultReadyEvents).doesNotThrowAnyException();

      verify(resultReadyHandler).handle(first);
      verify(resultReadyHandler).handle(second);
      verify(telemetry).resultReadyFailed();
    }
  }

  private SandboxExecution execution(
      String id,
      SandboxExecutionStatus status
  ) {
    return new SandboxExecution(
        id, "session-1", 10L, 1, SandboxWorkloadType.ALGORITHM,
        "two-sum", null, null, null,
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        status, null, null, null, null, null, null,
        null, LocalDateTime.now().minusMinutes(2), LocalDateTime.now(), null
    );
  }
}
