package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AlgorithmQueueTimeoutScheduler {

  private final AlgorithmPersistenceService persistenceService;
  private final AlgorithmResultReadyHandler resultReadyHandler;
  private final AlgorithmInterviewProperties properties;

  @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
  void degradeQueuedExecutions() {
    persistenceService.timeoutQueuedBefore(
        LocalDateTime.now().minus(properties.getQueuedTimeout())
    ).forEach(resultReadyHandler::handle);
  }
}
