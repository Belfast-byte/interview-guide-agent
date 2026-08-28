package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** 只生成一次无副作用动作提案；工具执行由 application 在 Intent 落库后编排。 */
public class BoundedActionRuntime {

  private final AgentModelGateway modelGateway;
  private final DeadlineExecutor deadlineExecutor;

  public BoundedActionRuntime(
      AgentModelGateway modelGateway,
      DeadlineExecutor deadlineExecutor
  ) {
    this.modelGateway = modelGateway;
    this.deadlineExecutor = deadlineExecutor;
  }

  public AgentAction propose(
      ReActRequest request,
      Duration deadline,
      Consumer<String> deltaSink
  ) {
    long deadlineNanos = System.nanoTime() + deadline.toNanos();
    ReActModelContext context = new ReActModelContext(request, List.of());
    return deadlineExecutor.invoke(
        () -> deltaSink == null
            ? modelGateway.nextAction(context)
            : modelGateway.nextActionStreaming(context, deltaSink),
        deadlineNanos,
        "Agent 动作提案"
    );
  }
}
