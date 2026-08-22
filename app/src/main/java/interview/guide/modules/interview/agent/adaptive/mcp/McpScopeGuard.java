package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/**
 * MCP scope 校验，凭证 scope 不足时写 FORBIDDEN 审计并拒绝调用。
 */
final class McpScopeGuard {

  private McpScopeGuard() {}

  static McpTenantPrincipal requireScope(
      McpSyncRequestContext context,
      String toolName,
      McpInterviewScope scope,
      AdaptiveMcpAuditService auditService
  ) {
    McpTenantPrincipal principal = (McpTenantPrincipal) context.transportContext()
        .get(McpTenantTransportConfiguration.PRINCIPAL_KEY);
    if (!principal.allows(scope)) {
      auditService.record(principal, toolName, null, McpAuditOutcome.FORBIDDEN);
      throw new BusinessException(ErrorCode.FORBIDDEN, "MCP scope 不足");
    }
    return principal;
  }
}
