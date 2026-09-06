package interview.guide.modules.interview.agent.adaptive.application;


/**
 * 答题推进过程的事件回调：阶段切换。
 * 同步路径使用 {@link #noop()}，SSE 路径由控制器提供真实实现。
 */
public interface AnswerEventSink {

  /** 推进阶段。 */
  enum AnswerStage {
    ASSESSING,
    GENERATING
  }

  void onStage(AnswerStage stage);

  default long deadlineNanos() { return Long.MAX_VALUE; }

  static AnswerEventSink noop() {
    return stage -> {};
  }
}
