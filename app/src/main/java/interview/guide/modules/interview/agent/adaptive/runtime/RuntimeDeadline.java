package interview.guide.modules.interview.agent.adaptive.runtime;

import java.time.Duration;

/** 一次 Agent Loop 内所有模型和只读 Tool 调用共享的绝对截止时间。 */
public record RuntimeDeadline(long deadlineNanos) {

  public static RuntimeDeadline start(Duration duration) {
    return new RuntimeDeadline(System.nanoTime() + duration.toNanos());
  }
}
