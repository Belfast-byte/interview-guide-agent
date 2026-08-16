package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP 租户凭据解析器。
 */
@Component
@RequiredArgsConstructor
public class McpTenantCredentialResolver {

  private static final String BEARER_PREFIX = "Bearer ";

  private final McpTenantProperties properties;

  public McpTenantPrincipal authenticate(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw unauthorized();
    }
    byte[] supplied = authorization.substring(BEARER_PREFIX.length())
        .getBytes(StandardCharsets.UTF_8);
    for (Map.Entry<String, McpTenantProperties.Credential> entry
        : properties.getCredentials().entrySet()) {
      McpTenantProperties.Credential credential = entry.getValue();
      if (MessageDigest.isEqual(
          credential.getApiKey().getBytes(StandardCharsets.UTF_8),
          supplied
      )) {
        return new McpTenantPrincipal(
            credential.getTenantId(),
            entry.getKey(),
            Set.copyOf(credential.getScopes())
        );
      }
    }
    throw unauthorized();
  }

  private BusinessException unauthorized() {
    return new BusinessException(ErrorCode.UNAUTHORIZED, "MCP 凭证无效");
  }
}
