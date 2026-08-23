package interview.guide.modules.interview.agent.adaptive.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 答题链路的后台执行器：显式配置的线程池，
 * 承载 SSE 流式答题任务与维度记忆（小结/声明）的异步生成任务。
 */
@Component
public class AdaptiveInterviewAnswerExecutor implements Executor {

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
      2,
      4,
      60L,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(100),
      runnable -> {
        Thread thread = new Thread(runnable, "adaptive-interview-answer");
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
