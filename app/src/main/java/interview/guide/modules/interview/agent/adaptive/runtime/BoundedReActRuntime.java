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

/**
 * 有界 ReAct 运行时，以最大步数、最大工具调用数和 deadline 约束驱动 Agent 循环，防止失控。
 */
public class BoundedReActRuntime {

  private final AgentModelGateway modelGateway;
  private final AgentToolExecutor toolExecutor;
  private final DeadlineExecutor deadlineExecutor;

  public BoundedReActRuntime(
      AgentModelGateway modelGateway,
      AgentToolExecutor toolExecutor
  ) {
    this(modelGateway, toolExecutor, new DeadlineExecutor());
  }

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
   * 执行一个有界的 ReAct 循环：在步数/工具调用数/deadline 限制内反复“模型决策→工具执行→观察反馈”，直到回复或预算耗尽。
   *
   * @param request ReAct 执行请求
   * @param budget 执行预算
   * @return 最终回复与工具执行轨迹
   */
  public ReActResult run(ReActRequest request, ReActBudget budget) {
    long deadlineNanos = System.nanoTime() + budget.deadline().toNanos();
    var observations = new ArrayList<ToolObservation>();
    var toolExecutions = new ArrayList<ToolExecution>();
    Set<ToolInvocation> toolInvocations = new HashSet<>();
    int toolCalls = 0;

    for (int step = 0; step < budget.maxSteps(); step++) {
      AgentAction action = deadlineExecutor.invoke(
          () -> modelGateway.nextAction(new ReActModelContext(request, observations)),
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
      if (toolCalls == budget.maxToolCalls()) {
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent 工具调用预算已用尽");
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
