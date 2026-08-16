package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.runtime.ReActBudget;
import java.util.Set;

/**
 * Agent 角色定义，包含角色标识、Prompt 路径、工具白名单和预算。
 */
public record AgentRoleDefinition(
    AgentRole role,
    ReActBudget budget,
    Set<String> allowedTools
) {

  public AgentRoleDefinition {
    allowedTools = Set.copyOf(allowedTools);
  }
}
