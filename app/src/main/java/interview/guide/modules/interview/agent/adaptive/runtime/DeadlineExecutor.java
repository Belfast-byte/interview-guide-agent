package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 带截止时间的任务执行器，在超时或中断时统一转换为业务异常。
 */
@Slf4j
@Component
public class DeadlineExecutor {

  /** 超时取消后等待被中断任务优雅退出的窗口。 */
  private static final long GRACEFUL_EXIT_TIMEOUT_MILLIS = 500;

  public <T> T invoke(
      Callable<T> invocation,
      long deadlineNanos,
      String operation
  ) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, operation + "超时");
    }

    FutureTask<T> task = new FutureTask<>(invocation);
    Thread worker = Thread.startVirtualThread(task);
    try {
      return task.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      awaitGracefulExit(worker, operation);
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, operation + "超时", e);
    } catch (InterruptedException e) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, operation + "被中断", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, operation + "失败", e.getCause());
    }
  }

  /**
   * 给被中断的任务一个短暂的优雅退出窗口；未退出说明底层调用仍在后台运行，记录日志后放弃。
   */
  private void awaitGracefulExit(Thread worker, String operation) {
    try {
      worker.join(GRACEFUL_EXIT_TIMEOUT_MILLIS);
      if (worker.isAlive()) {
        log.warn("{}超时后被中断的底层调用未在优雅窗口内退出，调用仍在后台运行", operation);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
