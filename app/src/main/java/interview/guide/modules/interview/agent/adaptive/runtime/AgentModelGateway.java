package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;

/**
 * 自适应 Agent 模型网关接口，根据 ReAct 上下文返回下一步动作。
 */
@FunctionalInterface
public interface AgentModelGateway {

  AgentAction nextAction(ReActModelContext context);
}
