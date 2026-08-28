package interview.guide.modules.interview.agent.adaptive.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class ActionIntentRecoveryScheduler {

  private final ActionIntentRecoveryService recoveryService;

  @Scheduled(
      fixedDelayString = "${app.interview.adaptive-agent.action-intent-recovery-delay}",
      initialDelayString = "${app.interview.adaptive-agent.action-intent-recovery-delay}"
  )
  public void recover() {
    recoveryService.recover();
  }
}
