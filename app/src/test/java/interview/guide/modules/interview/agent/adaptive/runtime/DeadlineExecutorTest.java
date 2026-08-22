package interview.guide.modules.interview.agent.adaptive.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadlineExecutorTest {

  private static final Duration INVOCATION_TIMEOUT = Duration.ofMillis(10);
  private static final Duration MINIMUM_GRACEFUL_WAIT = Duration.ofMillis(400);

  @Test
  @DisplayName("超时后根据工作线程状态等待忽略中断的调用退出")
  void shouldWaitForInterruptIgnoringWorker() {
    DeadlineExecutor executor = new DeadlineExecutor();
    AtomicBoolean running = new AtomicBoolean(true);
    long startedAt = System.nanoTime();

    try {
      assertThatThrownBy(() -> executor.invoke(
          () -> runUntilReleased(running),
          System.nanoTime() + INVOCATION_TIMEOUT.toNanos(),
          "测试调用"
      )).isInstanceOf(BusinessException.class)
          .hasMessage("测试调用超时");
      assertThat(Duration.ofNanos(
          System.nanoTime() - startedAt
      )).isGreaterThanOrEqualTo(MINIMUM_GRACEFUL_WAIT);
    } finally {
      running.set(false);
    }
  }

  private String runUntilReleased(AtomicBoolean running) {
    while (running.get()) {
      Thread.interrupted();
      Thread.onSpinWait();
    }
    return "done";
  }
}
