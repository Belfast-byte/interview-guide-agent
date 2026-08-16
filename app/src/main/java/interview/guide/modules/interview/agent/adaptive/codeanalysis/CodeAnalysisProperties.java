package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码分析配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.interview.code-analysis")
public class CodeAnalysisProperties {

  private Duration retention = Duration.ofDays(30);
  private Duration timeout = Duration.ofMinutes(10);
  private int maxSnapshotBytes = 100 * 1024 * 1024;
  private int maxSnapshotFiles = 20_000;
  private long maxTokenCost = 200_000;
  private String workerToken;
}
