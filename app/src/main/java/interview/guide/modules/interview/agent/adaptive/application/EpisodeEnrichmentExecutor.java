package interview.guide.modules.interview.agent.adaptive.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Episode enrichment 专用有界执行器，线程与队列独立于交互答题。
 */
@Component
public class EpisodeEnrichmentExecutor implements Executor {

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
      2,
      4,
      60L,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(100),
      runnable -> {
        Thread thread = new Thread(runnable, "adaptive-episode-enrichment");
        thread.setDaemon(true);
        return thread;
      },
      new ThreadPoolExecutor.AbortPolicy()
  );

  @Override
  public void execute(Runnable task) {
    executor.execute(task);
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
