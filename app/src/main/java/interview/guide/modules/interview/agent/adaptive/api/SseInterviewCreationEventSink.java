package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.adaptive.application.InterviewCreationEventSink;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import java.util.function.Consumer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 将面试创建事件映射为 created、delta、done、error SSE 事件。 */
final class SseInterviewCreationEventSink implements InterviewCreationEventSink {

  private final SseEventSender sender;

  SseInterviewCreationEventSink(SseEmitter emitter) {
    this.sender = new SseEventSender(emitter);
  }

  @Override
  public void onCreated(PlannedInterview skeleton) {
    sender.send("created", AdaptiveInterviewResponse.from(skeleton));
  }

  @Override
  public Consumer<String> deltaSink() {
    return delta -> sender.send("delta", delta);
  }

  @Override
  public void onCompleted(PlannedInterview interview) {
    sender.send("done", AdaptiveInterviewResponse.from(interview));
    sender.complete();
  }

  @Override
  public void onFailed(String message) {
    sender.send("error", Result.error(ErrorCode.AI_SERVICE_ERROR.getCode(), message));
    sender.complete();
  }
}
