package interview.guide.modules.interview.agent.adaptive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AdaptiveExecutorIsolationTest {

  @Test
  @DisplayName("记忆线程和队列全部占满时，答题仍可独立执行")
  void shouldRunAnswerWhileEnrichmentPoolIsSaturated() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(4);
    try (var context = new AnnotationConfigApplicationContext(
        AdaptiveInterviewAnswerExecutor.class, EpisodeEnrichmentExecutor.class)) {
      var enrichment = context.getBean(EpisodeEnrichmentExecutor.class);
      var answers = context.getBean(AdaptiveInterviewAnswerExecutor.class);
      Runnable blocked = () -> {
        started.countDown();
        try {
          release.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      };
      try {
        // 4 个工作线程 + 100 个排队任务，全部占满记忆执行器。
        for (int i = 0; i < 104; i++) enrichment.execute(blocked);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> enrichment.execute(() -> {}))
            .isInstanceOf(RejectedExecutionException.class);

        CompletableFuture<String> answerThread = new CompletableFuture<>();
        answers.execute(() -> answerThread.complete(Thread.currentThread().getName()));
        assertThat(answerThread.get(5, TimeUnit.SECONDS)).startsWith("adaptive-interview-answer");
      } finally {
        release.countDown();
      }
    }
  }
}
