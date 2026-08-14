package interview.guide.modules.interview.agent.adaptive.mcp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.interview.adaptive-agent.mcp.question-bank")
public class McpQuestionBankProperties {

  private boolean enabled;

  @NotBlank
  private String serverName = "question-bank";

  @NotBlank
  private String toolName = "question_bank_search";

  private Duration deadline = Duration.ofSeconds(3);

  @Min(1)
  @Max(100_000)
  private int maxResponseChars = 20_000;
}
