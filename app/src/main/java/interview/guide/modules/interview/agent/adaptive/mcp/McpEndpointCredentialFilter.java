package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class McpEndpointCredentialFilter extends OncePerRequestFilter {

  private final McpTenantCredentialResolver credentialResolver;
  private final McpServerStreamableHttpProperties serverProperties;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !serverProperties.getMcpEndpoint().equals(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    try {
      request.setAttribute(
          McpTenantTransportConfiguration.PRINCIPAL_KEY,
          credentialResolver.authenticate(request.getHeader("Authorization"))
      );
    } catch (BusinessException e) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
