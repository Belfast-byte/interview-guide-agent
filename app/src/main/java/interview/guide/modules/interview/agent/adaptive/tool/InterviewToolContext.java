package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.JsonNode;

/** 本次推理的可信身份、资源和来源；不在 singleton 或 ThreadLocal 中保存请求数据。 */
public final class InterviewToolContext implements AutoCloseable {
  private static final String KEY = InterviewToolContext.class.getName();
  private final AgentContext context;
  private final long deadlineNanos;
  private final int maxReadCalls;
  private final Set<RequestKey> executed = new HashSet<>();
  private final List<DecisionObservation> observations = new ArrayList<>();
  private int readCalls;
  private boolean closed;

  public InterviewToolContext(AgentContext context, long deadlineNanos, int maxReadCalls) {
    this.context = context;
    this.deadlineNanos = deadlineNanos;
    this.maxReadCalls = maxReadCalls;
  }

  public static InterviewToolContext from(ToolContext context) {
    Object value = context.getContext().get(KEY);
    if (!(value instanceof InterviewToolContext scope)) {
      throw new IllegalStateException("缺少可信面试工具上下文");
    }
    scope.requireActive();
    return scope;
  }

  public Map<String, Object> values() { return Map.of(KEY, this); }
  public AgentContext context() { return context; }
  public long deadlineNanos() { return deadlineNanos; }
  public synchronized List<DecisionObservation> observations() { return List.copyOf(observations); }

  public synchronized void requireActive() {
    if (closed || System.nanoTime() >= deadlineNanos) {
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "Interview Agent 资源截止时间已耗尽");
    }
  }

  synchronized void admitRead(String name, JsonNode arguments) {
    requireActive();
    if (!context.facts().allowedReadTools().contains(name)) {
      throw new ReadToolValidationException("toolName", "工具不在当前会话白名单中");
    }
    if (!executed.add(new RequestKey(name, arguments))) {
      throw new ReadToolValidationException("arguments", "相同工具和参数已执行，请使用已有结果或调整请求");
    }
    if (++readCalls > maxReadCalls) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "本轮工具调用次数已达上限，请重试");
    }
  }

  public synchronized DecisionObservation observe(String name, ReadToolResult result) {
    requireActive();
    var observation = switch (result) {
      case ReadToolResult.Success success -> observation(name, DecisionObservation.Kind.TOOL_SUCCESS,
          null, null, success.data(), success.adoptableSources());
      case ReadToolResult.Empty empty -> observation(name, DecisionObservation.Kind.TOOL_EMPTY,
          null, empty.message(), Map.of(), List.of());
      case ReadToolResult.Timeout timeout -> observation(name, DecisionObservation.Kind.TOOL_TIMEOUT,
          null, timeout.message(), Map.of(), List.of());
      case ReadToolResult.Error error -> observation(name, DecisionObservation.Kind.TOOL_ERROR,
          null, error.message(), Map.of(), List.of());
    };
    observations.add(observation);
    return observation;
  }

  synchronized DecisionObservation reject(String name, String field, String message) {
    requireActive();
    var result = observation(name, DecisionObservation.Kind.VALIDATION_REJECTION,
        field, message, Map.of(), List.of());
    observations.add(result);
    return result;
  }

  private DecisionObservation observation(String name, DecisionObservation.Kind kind,
      String field, String message, Map<String, Object> data,
      List<DecisionObservation.AdoptableSource> sources) {
    return new DecisionObservation("tool-" + observations.size(), kind, field, message, name, data, sources);
  }

  @Override
  public synchronized void close() { closed = true; }

  private record RequestKey(String name, JsonNode arguments) {}
}
