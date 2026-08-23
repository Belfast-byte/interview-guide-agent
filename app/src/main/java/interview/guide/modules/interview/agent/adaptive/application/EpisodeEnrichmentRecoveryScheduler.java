package interview.guide.modules.interview.agent.adaptive.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期扫描 DB，恢复未投递 PENDING 与超时 PROCESSING Episode。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class EpisodeEnrichmentRecoveryScheduler {

  private final EpisodeEnrichmentRecoveryService recoveryService;

  @Scheduled(
      fixedDelayString = "${app.interview.adaptive-agent.episode-enrichment-recovery-delay}",
      initialDelayString = "${app.interview.adaptive-agent.episode-enrichment-recovery-delay}"
  )
  public void recover() {
    recoveryService.recover();
  }
}
