package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;

/**
 * Agent 工具执行器接口，执行模型请求的工具调用并返回执行结果。
 */
@FunctionalInterface
public interface AgentToolExecutor {

  ToolExecution execute(ReActRequest request, ToolCallAction action);
}
