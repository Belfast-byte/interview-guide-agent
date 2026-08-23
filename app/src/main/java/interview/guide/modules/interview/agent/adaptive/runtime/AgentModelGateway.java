package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import java.util.function.Consumer;

/**
 * 自适应 Agent 模型网关接口，根据 ReAct 上下文返回下一步动作。
 */
@FunctionalInterface
public interface AgentModelGateway {

  AgentAction nextAction(ReActModelContext context);

  /**
   * 流式变体：产出动作的同时把模型原始文本增量推给 deltaSink。
   * 默认退化为非流式，仅真实模型网关覆盖。
   */
  default AgentAction nextActionStreaming(ReActModelContext context, Consumer<String> deltaSink) {
    return nextAction(context);
  }
}
