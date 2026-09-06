package interview.guide.modules.interview.agent.adaptive.rubric;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RubricSchedulerIsolationTest {
  @Test void blockedRubricTaskDoesNotBlockOtherScheduledWork() throws Exception {
    var configuration=new RubricGenerationConfiguration();
    var rubric=configuration.scheduler();
    var regular=configuration.defaultScheduler();
    rubric.initialize(); regular.initialize();
    var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
    try {
      rubric.submit(() -> { entered.countDown(); try { release.await(); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
      assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
      assertThat(regular.submit(() -> "ok").get(2,TimeUnit.SECONDS)).isEqualTo("ok");
    } finally { release.countDown(); rubric.shutdown(); regular.shutdown(); }
  }
}
