package interview.guide.modules.interview.agent.adaptive.api;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 向同一个 SseEmitter 串行发送具名事件，并在客户端断开后停止发送。 */
final class SseEventSender {

  private final SseEmitter emitter;
  private final AtomicBoolean broken = new AtomicBoolean();

  SseEventSender(SseEmitter emitter) {
    this.emitter = emitter;
  }

  void send(String name, Object data) {
    if (broken.get()) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().name(name).data(data));
    } catch (IOException | IllegalStateException e) {
      broken.set(true);
    }
  }

  void complete() {
    emitter.complete();
  }
}
