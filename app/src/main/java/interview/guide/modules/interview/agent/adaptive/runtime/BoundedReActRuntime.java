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

  public RespondAction run(ReActRequest request, ReActBudget budget) {
    long deadlineNanos = System.nanoTime() + budget.deadline().toNanos();
    var observations = new ArrayList<ToolObservation>();
    Set<ToolInvocation> toolInvocations = new HashSet<>();
    int toolCalls = 0;

    for (int step = 0; step < budget.maxSteps(); step++) {
      AgentAction action = deadlineExecutor.invoke(
          () -> modelGateway.nextAction(new ReActModelContext(request, observations)),
          deadlineNanos,
          "Agent 面试执行"
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

      String output = deadlineExecutor.invoke(
          () -> toolExecutor.execute(toolCall),
          deadlineNanos,
          "Agent 面试执行"
      );
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
  private record ToolInvocation(String toolName, Map<String, Object> arguments) {}
}
