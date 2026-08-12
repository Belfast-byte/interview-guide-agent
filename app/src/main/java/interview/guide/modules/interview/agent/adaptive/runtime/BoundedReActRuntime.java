package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BoundedReActRuntime {

  private final AgentModelGateway modelGateway;
  private final AgentToolExecutor toolExecutor;

  public RespondAction run(ReActRequest request, ReActBudget budget) {
    long deadlineNanos = System.nanoTime() + budget.deadline().toNanos();
    var observations = new ArrayList<ToolObservation>();
    Set<ToolInvocation> toolInvocations = new HashSet<>();
    int toolCalls = 0;

    for (int step = 0; step < budget.maxSteps(); step++) {
      AgentAction action = invokeBeforeDeadline(
          () -> modelGateway.nextAction(new ReActModelContext(request, observations)),
          deadlineNanos
      );
      if (action instanceof RespondAction respondAction) {
        return respondAction;
      }

      ToolCallAction toolCall = (ToolCallAction) action;
      ToolInvocation invocation = new ToolInvocation(toolCall.toolName(), toolCall.arguments());
      if (!toolInvocations.add(invocation)) {
        observations.add(new ToolObservation(
            toolCall.toolName(),
            toolCall.arguments(),
            false,
            "相同工具和参数已调用，本次重复调用被拒绝"
        ));
        continue;
      }
      if (toolCalls == budget.maxToolCalls()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 工具调用预算已用尽");
      }

      String output = invokeBeforeDeadline(() -> toolExecutor.execute(toolCall), deadlineNanos);
      observations.add(new ToolObservation(
          toolCall.toolName(),
          toolCall.arguments(),
          true,
          output
      ));
      toolCalls++;
    }

    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 模型步预算已用尽");
  }

  private <T> T invokeBeforeDeadline(Callable<T> invocation, long deadlineNanos) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "Agent 面试执行超时");
    }

    FutureTask<T> task = new FutureTask<>(invocation);
    Thread.startVirtualThread(task);
    try {
      return task.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      task.cancel(true);
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "Agent 面试执行超时", e);
    } catch (InterruptedException e) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "Agent 面试执行被中断", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 面试执行失败", e.getCause());
    }
  }

  private record ToolInvocation(String toolName, Map<String, Object> arguments) {}
}
