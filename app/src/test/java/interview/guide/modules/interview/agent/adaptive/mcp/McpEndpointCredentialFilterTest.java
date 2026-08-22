package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpEndpointCredentialFilterTest {

  @Mock
  private McpTenantCredentialResolver credentialResolver;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  private McpEndpointCredentialFilter filter;

  @BeforeEach
  void setUp() {
    McpServerStreamableHttpProperties properties =
        new McpServerStreamableHttpProperties();
    filter = new McpEndpointCredentialFilter(credentialResolver, properties);
    when(request.getServletPath()).thenReturn(properties.getMcpEndpoint());
  }

  @Test
  @DisplayName("MCP 请求在进入协议处理器前校验凭证")
  void shouldAuthenticateBeforeTransport() throws Exception {
    McpTenantPrincipal principal = new McpTenantPrincipal(
        "tenant-a",
        "credential-a",
        java.util.Set.of(McpInterviewScope.INTERVIEW_READ)
    );
    when(request.getHeader("Authorization")).thenReturn("Bearer secret-a");
    when(credentialResolver.authenticate("Bearer secret-a")).thenReturn(principal);

    filter.doFilter(request, response, filterChain);

    verify(request).setAttribute(
        McpTenantTransportConfiguration.PRINCIPAL_KEY,
        principal
    );
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("无效 MCP 凭证返回 HTTP 401 且不进入协议处理器")
  void shouldReturnUnauthorized() throws Exception {
    when(credentialResolver.authenticate(null)).thenThrow(
        new BusinessException(ErrorCode.UNAUTHORIZED, "MCP 凭证无效")
    );

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(filterChain, never()).doFilter(request, response);
  }
}
