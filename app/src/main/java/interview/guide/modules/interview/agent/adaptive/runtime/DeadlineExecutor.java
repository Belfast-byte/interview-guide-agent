package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * 带截止时间的任务执行器，在超时或中断时统一转换为业务异常。
 */
@Component
public class DeadlineExecutor {

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
    Thread.startVirtualThread(task);
    try {
      return task.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
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
}
