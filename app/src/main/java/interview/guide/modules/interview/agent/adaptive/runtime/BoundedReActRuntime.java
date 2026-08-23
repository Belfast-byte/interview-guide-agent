package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 有界 ReAct 运行时，以最大步数、最大工具调用数和 deadline 约束驱动 Agent 循环，防止失控。
 */
public class BoundedReActRuntime {

  private final AgentModelGateway modelGateway;
  private final AgentToolExecutor toolExecutor;
  private final DeadlineExecutor deadlineExecutor;

  public BoundedReActRuntime(
      AgentModelGateway modelGateway,
      AgentToolExecutor toolExecutor,
      DeadlineExecutor deadlineExecutor
  ) {
    this.modelGateway = modelGateway;
    this.toolExecutor = toolExecutor;
    this.deadlineExecutor = deadlineExecutor;
  }

  /**
   * 执行一个有界的 ReAct 循环：在步数/工具调用数/deadline 限制内反复“模型决策→工具执行→观察反馈”，直到模型给出回复。
   * 步数或工具调用预算耗尽时先拒绝本次工具调用并要求模型立即给出最终回复，仅当模型仍坚持调用工具时才失败。
   *
   * @param request ReAct 执行请求
   * @param budget 执行预算
   * @return 最终回复与工具执行轨迹
   */
  public ReActResult run(ReActRequest request, ReActBudget budget) {
    return runStreaming(request, budget, null);
  }

  /**
   * 带流式回调的变体：每个模型步骤都经 deltaSink 推送原始文本增量，
   * 确保工具调用后的最终回复仍可流式输出；deltaSink 为 null 时与 {@link #run} 等价。
   */
  public ReActResult runStreaming(ReActRequest request, ReActBudget budget, Consumer<String> deltaSink) {
    long deadlineNanos = System.nanoTime() + budget.deadline().toNanos();
    var observations = new ArrayList<ToolObservation>();
    var toolExecutions = new ArrayList<ToolExecution>();
    Set<ToolInvocation> toolInvocations = new HashSet<>();
    int toolCalls = 0;
    int maxSteps = budget.maxSteps();
    boolean finalReplyDemanded = false;

    for (int step = 0; step < maxSteps; step++) {
      Consumer<String> stepSink = deltaSink;
      AgentAction action = deadlineExecutor.invoke(
          () -> stepSink == null
              ? modelGateway.nextAction(new ReActModelContext(request, observations))
              : modelGateway.nextActionStreaming(new ReActModelContext(request, observations), stepSink),
          deadlineNanos,
          "Agent 面试执行"
      );
      if (action instanceof RespondAction respondAction) {
        return new ReActResult(respondAction, toolExecutions);
      }

      ToolCallAction toolCall = (ToolCallAction) action;
      ToolInvocation invocation = new ToolInvocation(toolCall.toolName(), toolCall.arguments());
      if (!toolInvocations.add(invocation)) {
        observations.add(new ToolObservation(
            toolCall.toolName(),
            toolCall.arguments(),
            false,
            null,
            "相同工具和参数已调用，本次重复调用被拒绝"
        ));
        continue;
      }
      if (toolCalls == budget.maxToolCalls() || step == maxSteps - 1) {
        if (finalReplyDemanded) {
          throw new BusinessException(
              ErrorCode.AI_SERVICE_ERROR,
              "Agent 执行预算已用尽，且模型在收到预算耗尽通知后仍坚持调用工具"
          );
        }
        finalReplyDemanded = true;
        maxSteps = budget.maxSteps() + 1;
        observations.add(new ToolObservation(
            toolCall.toolName(),
            toolCall.arguments(),
            false,
            null,
            "执行预算已用完，本次工具调用被拒绝，请立即给出最终回复，不要再调用任何工具"
        ));
        continue;
      }

      ToolExecution execution = deadlineExecutor.invoke(
          () -> toolExecutor.execute(request, toolCall),
          deadlineNanos,
          "Agent 面试执行"
      );
      toolExecutions.add(execution);
      observations.add(new ToolObservation(
          toolCall.toolName(),
          toolCall.arguments(),
          true,
          execution.resultId(),
          execution.output()
      ));
      toolCalls++;
    }

    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 模型步预算已用尽");
  }

  private record ToolInvocation(String toolName, Map<String, Object> arguments) {}
}
