package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.ErrorCode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTenantCredentialResolverTest {

  @Test
  @DisplayName("租户身份和 scope 只由 Bearer 凭证解析")
  void shouldResolveTenantFromBearerCredential() {
    McpTenantProperties properties = new McpTenantProperties();
    McpTenantProperties.Credential credential = new McpTenantProperties.Credential();
    credential.setTenantId("tenant-a");
    credential.setApiKey("secret-a");
    credential.setScopes(Set.of(McpInterviewScope.INTERVIEW_READ));
    properties.setCredentials(Map.of("credential-a", credential));

    McpTenantPrincipal principal = new McpTenantCredentialResolver(properties)
        .authenticate("Bearer secret-a");

    assertThat(principal.tenantId()).isEqualTo("tenant-a");
    assertThat(principal.credentialId()).isEqualTo("credential-a");
    assertThat(principal.scopes()).containsExactly(McpInterviewScope.INTERVIEW_READ);
  }

  @Test
  @DisplayName("未知凭证快速失败为未授权")
  void shouldRejectUnknownCredential() {
    McpTenantProperties properties = new McpTenantProperties();

    assertThatThrownBy(() -> new McpTenantCredentialResolver(properties)
        .authenticate("Bearer unknown"))
        .hasFieldOrPropertyWithValue("code", ErrorCode.UNAUTHORIZED.getCode());
  }
}
