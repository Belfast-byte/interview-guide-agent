package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.AgentAction;

@FunctionalInterface
public interface AgentModelGateway {

  AgentAction nextAction(ReActModelContext context);
}
