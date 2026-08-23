package interview.guide.modules.interview.agent.adaptive.application;

import java.util.function.Consumer;

/**
 * 答题推进过程的事件回调：阶段切换与决策增量文本。
 * 同步路径使用 {@link #noop()}，SSE 路径由控制器提供真实实现。
 */
public interface AnswerEventSink {

  /** 推进阶段。 */
  enum AnswerStage {
    ASSESSING,
    GENERATING
  }

  void onStage(AnswerStage stage);

  /** 决策阶段的增量文本回调；返回 null 表示不流式（同步路径）。 */
  default Consumer<String> deltaSink() {
    return null;
  }

  static AnswerEventSink noop() {
    return stage -> {};
  }
}
