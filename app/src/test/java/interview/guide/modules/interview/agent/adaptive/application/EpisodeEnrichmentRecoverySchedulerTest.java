package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class EpisodeEnrichmentRecoverySchedulerTest {

  @Test
  @DisplayName("周期恢复入口委托 DB 驱动的 enrichment 恢复服务")
  void shouldDelegateScheduledRecovery() throws NoSuchMethodException {
    EpisodeEnrichmentRecoveryService service = mock(
        EpisodeEnrichmentRecoveryService.class
    );
    EpisodeEnrichmentRecoveryScheduler scheduler =
        new EpisodeEnrichmentRecoveryScheduler(service);

    scheduler.recover();

    verify(service).recover();
    Method method = EpisodeEnrichmentRecoveryScheduler.class.getMethod("recover");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);
    assertThat(scheduled.fixedDelayString())
        .contains("episode-enrichment-recovery-delay");
  }
}
