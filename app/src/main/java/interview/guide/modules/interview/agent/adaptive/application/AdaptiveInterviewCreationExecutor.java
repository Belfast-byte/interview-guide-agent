package interview.guide.modules.interview.agent.adaptive.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 创建链路的后台执行器：显式配置的线程池，承载规划与首题生成。
 */
@Component
class AdaptiveInterviewCreationExecutor implements AdaptiveInterviewCreationTaskRunner {

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
      2,
      4,
      60L,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(100),
      runnable -> {
        Thread thread = new Thread(runnable, "adaptive-interview-creation");
        thread.setDaemon(true);
        return thread;
      },
      new ThreadPoolExecutor.AbortPolicy()
  );

  @Override
  public void submit(Runnable task) {
    executor.execute(task);
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
