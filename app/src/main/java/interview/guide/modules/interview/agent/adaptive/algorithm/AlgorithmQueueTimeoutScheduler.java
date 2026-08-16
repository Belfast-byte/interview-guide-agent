package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.time.LocalDateTime;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 算法队列超时调度器，处理长时间未完成评测的任务。
 */
@Component
@RequiredArgsConstructor
class AlgorithmQueueTimeoutScheduler {

  private final AlgorithmPersistenceService persistenceService;
  private final AlgorithmResultReadyHandler resultReadyHandler;
  private final AlgorithmInterviewProperties properties;
  private final AlgorithmInterviewTelemetry telemetry;

  @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
  void degradeQueuedExecutions() {
    persistenceService.timeoutQueuedBefore(
        LocalDateTime.now().minus(properties.getQueuedTimeout())
    ).forEach(execution -> {
      telemetry.degraded();
      resultReadyHandler.handle(execution);
    });
  }
}
