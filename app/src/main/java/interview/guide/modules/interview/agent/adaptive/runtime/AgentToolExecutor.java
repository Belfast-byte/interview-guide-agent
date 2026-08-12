package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;

@FunctionalInterface
public interface AgentToolExecutor {

  ToolExecution execute(ReActRequest request, ToolCallAction action);
}
