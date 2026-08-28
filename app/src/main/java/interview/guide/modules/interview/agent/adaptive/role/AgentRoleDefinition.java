package interview.guide.modules.interview.agent.adaptive.role;

import java.time.Duration;
import java.util.Set;

/**
 * Agent 角色定义，包含角色标识、调用截止时间和工具白名单。
 */
public record AgentRoleDefinition(
    AgentRole role,
    Duration deadline,
    Set<String> allowedTools
) {

  public AgentRoleDefinition {
    allowedTools = Set.copyOf(allowedTools);
  }
}
