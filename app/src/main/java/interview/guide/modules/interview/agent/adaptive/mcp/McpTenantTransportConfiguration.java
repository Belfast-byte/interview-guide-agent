package interview.guide.modules.interview.agent.adaptive.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 租户传输配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class McpTenantTransportConfiguration {

  static final String PRINCIPAL_KEY = "adaptive.mcp.tenant-principal";

  @Bean
  WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
      JsonMapper jsonMapper,
      McpServerStreamableHttpProperties properties
  ) {
    return WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(properties.getMcpEndpoint())
        .keepAliveInterval(properties.getKeepAliveInterval())
        .disallowDelete(properties.isDisallowDelete())
        .contextExtractor(request -> McpTransportContext.create(Map.of(
            PRINCIPAL_KEY,
            request.servletRequest().getAttribute(PRINCIPAL_KEY)
        )))
        .build();
  }
}
