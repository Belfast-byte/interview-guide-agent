package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview.code-analysis")
public class CodeAnalysisProperties {

  private Duration retention = Duration.ofDays(30);
}
