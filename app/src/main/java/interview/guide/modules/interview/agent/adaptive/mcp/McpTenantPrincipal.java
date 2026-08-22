package interview.guide.modules.interview.agent.adaptive.mcp;

import java.util.Set;

/**
 * MCP 租户主体。
 */
public record McpTenantPrincipal(
    String tenantId,
    String credentialId,
    Set<McpInterviewScope> scopes
) {

  public boolean allows(McpInterviewScope scope) {
    return scopes.contains(scope);
  }
}
