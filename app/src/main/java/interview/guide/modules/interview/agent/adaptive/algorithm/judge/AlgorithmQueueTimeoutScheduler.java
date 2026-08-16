package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 算法队列超时与结果就绪对账调度器：
 * <ul>
 *   <li>回收 PENDING 超龄任务并降级唤醒编排器；</li>
 *   <li>回收 RUNNING 超龄任务（消费者在 markRunning 后崩溃会遗留 RUNNING 卡死记录），
 *       标记基础设施失败并降级唤醒；</li>
 *   <li>补偿“判题已落库但唤醒未送达”的执行（resultReadyHandler 抛异常后消息被 ACK 丢弃的路径）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AlgorithmQueueTimeoutScheduler {

  private final AlgorithmPersistenceService persistenceService;
  private final AlgorithmResultReadyHandler resultReadyHandler;
  private final AlgorithmInterviewProperties properties;
  private final AlgorithmInterviewTelemetry telemetry;
  private final AlgorithmResultReadyDeliveryStore resultReadyDeliveryStore;

  @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
  void degradeQueuedExecutions() {
    persistenceService.timeoutQueuedBefore(
        LocalDateTime.now().minus(properties.getQueuedTimeout())
    ).forEach(this::degradeAndNotify);
  }

  @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
  void degradeStuckRunningExecutions() {
    persistenceService.timeoutRunningBefore(
        LocalDateTime.now().minus(properties.getRunningTimeout())
    ).forEach(execution -> {
      telemetry.stuckRunningTimedOut();
      degradeAndNotify(execution);
    });
  }

  @Scheduled(fixedDelay = 10_000, initialDelay = 20_000)
  void redeliverMissingResultReadyEvents() {
    LocalDateTime settledBefore = LocalDateTime.now().minus(
        properties.getResultReadyRedeliveryGrace()
    );
    resultReadyDeliveryStore.findUndeliveredBefore(
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        settledBefore
    ).forEach(executionId -> {
      try {
        telemetry.resultReadyRedelivered();
        resultReadyHandler.handle(persistenceService.getExecution(executionId));
      } catch (Exception e) {
        telemetry.resultReadyFailed();
        log.error("补偿重投结果就绪唤醒失败 executionId={}", executionId, e);
      }
    });
  }

  private void degradeAndNotify(SandboxExecution execution) {
    telemetry.degraded();
    notifyResultReady(execution);
  }

  private void notifyResultReady(SandboxExecution execution) {
    try {
      resultReadyHandler.handle(execution);
    } catch (Exception e) {
      telemetry.resultReadyFailed();
      log.error("结果就绪唤醒失败,等待补偿扫描重投 executionId={}", execution.id(), e);
    }
  }
}
