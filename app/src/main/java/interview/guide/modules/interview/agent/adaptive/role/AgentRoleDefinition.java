package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import java.util.Set;

public record AgentRoleDefinition(
    AgentRole role,
    ReActBudget budget,
    Set<String> allowedTools
) {

  public AgentRoleDefinition {
    allowedTools = Set.copyOf(allowedTools);
  }
}
