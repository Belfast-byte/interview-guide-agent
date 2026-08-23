package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.function.Consumer;

/** 创建面试过程的事件回调：骨架、首题增量、完成或失败。 */
public interface InterviewCreationEventSink {

  void onCreated(PlannedInterview skeleton);

  Consumer<String> deltaSink();

  void onCompleted(PlannedInterview interview);

  void onFailed(String message);

  static InterviewCreationEventSink noop() {
    return new InterviewCreationEventSink() {
      @Override
      public void onCreated(PlannedInterview skeleton) {}

      @Override
      public Consumer<String> deltaSink() {
        return null;
      }

      @Override
      public void onCompleted(PlannedInterview interview) {}

      @Override
      public void onFailed(String message) {}
    };
  }
}
