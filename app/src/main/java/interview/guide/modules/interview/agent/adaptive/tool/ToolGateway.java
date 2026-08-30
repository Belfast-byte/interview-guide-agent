package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.Kind;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolBatch;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolCall;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 请求内按模型顺序执行只读工具，不持久化任何中间执行状态。 */
@Slf4j
@Component
public class ToolGateway implements ReadToolExecutor {

  private static final String DEADLINE_MESSAGE = "Interview Agent 资源截止时间已耗尽";
  private static final String TOOL_ERROR_MESSAGE = "只读工具执行失败";

  private final Map<String, ReadOnlyAgentTool> tools;

  public ToolGateway(List<ReadOnlyAgentTool> tools) {
    this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(
        ReadOnlyAgentTool::name,
        Function.identity()
    ));
  }

  @Override
  public List<DecisionObservation> execute(ReadToolBatch batch) {
    List<DecisionObservation> observations = new ArrayList<>(batch.calls().size());
    for (int callIndex = 0; callIndex < batch.calls().size(); callIndex++) {
      requireTimeRemaining(batch.deadlineNanos());
      observations.add(executeCall(batch, batch.calls().get(callIndex), callIndex));
    }
    return List.copyOf(observations);
  }

  private DecisionObservation executeCall(
      ReadToolBatch batch,
      ReadToolCall call,
      int callIndex
  ) {
    String reference = "tool-" + batch.batchIndex() + "-" + callIndex;
    ReadOnlyAgentTool tool = tools.get(call.toolName());
    if (!batch.context().facts().allowedReadTools().contains(call.toolName())) {
      return rejection(reference, call, "toolName", "工具不在当前会话白名单中");
    }
    if (tool == null) {
      return rejection(reference, call, "toolName", "工具未装配");
    }
    ReadToolRequest request = new ReadToolRequest(
        batch.context(), call.arguments(), batch.deadlineNanos());
    try {
      tool.validate(request);
      requireTimeRemaining(batch.deadlineNanos());
      return observation(reference, call, tool.execute(request));
    } catch (ReadToolValidationException e) {
      return rejection(reference, call, e.field(), e.getMessage());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "只读工具执行异常: sessionId={}, toolName={}, reference={}",
          batch.context().session().identity().sessionId(),
          call.toolName(),
          reference,
          e
      );
      return envelope(
          reference, Kind.TOOL_ERROR, null, TOOL_ERROR_MESSAGE,
          call.toolName(), Map.of(), List.of());
    }
  }

  private DecisionObservation observation(
      String reference,
      ReadToolCall call,
      ReadToolResult result
  ) {
    return switch (result) {
      case ReadToolResult.Success success -> envelope(
          reference, Kind.TOOL_SUCCESS, null, null, call.toolName(),
          success.data(), success.adoptableSources());
      case ReadToolResult.Empty empty -> envelope(
          reference, Kind.TOOL_EMPTY, null, empty.message(), call.toolName(),
          Map.of(), List.of());
      case ReadToolResult.Timeout timeout -> envelope(
          reference, Kind.TOOL_TIMEOUT, null, timeout.message(), call.toolName(),
          Map.of(), List.of());
      case ReadToolResult.Error error -> envelope(
          reference, Kind.TOOL_ERROR, null, error.message(), call.toolName(),
          Map.of(), List.of());
    };
  }

  private DecisionObservation rejection(
      String reference,
      ReadToolCall call,
      String field,
      String message
  ) {
    return envelope(
        reference, Kind.VALIDATION_REJECTION, field, message,
        call.toolName(), Map.of(), List.of());
  }

  private DecisionObservation envelope(
      String reference,
      Kind kind,
      String field,
      String message,
      String toolName,
      Map<String, Object> data,
      List<DecisionObservation.AdoptableSource> sources
  ) {
    return new DecisionObservation(
        reference, kind, field, message, toolName, data, sources);
  }

  private void requireTimeRemaining(long deadlineNanos) {
    if (System.nanoTime() >= deadlineNanos) {
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, DEADLINE_MESSAGE);
    }
  }
}
