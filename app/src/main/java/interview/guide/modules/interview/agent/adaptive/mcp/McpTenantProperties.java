package interview.guide.modules.interview.agent.adaptive.mcp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.interview.adaptive-agent.mcp.server")
public class McpTenantProperties {

  @Valid
  private Map<String, Credential> credentials = new LinkedHashMap<>();

  @Getter
  @Setter
  public static class Credential {

    @NotBlank
    private String tenantId;

    @NotBlank
    private String apiKey;

    @NotEmpty
    private Set<McpInterviewScope> scopes;
  }
}
