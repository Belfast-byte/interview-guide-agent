package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;

@FunctionalInterface
public interface AgentToolExecutor {

  String execute(ToolCallAction action);
}
