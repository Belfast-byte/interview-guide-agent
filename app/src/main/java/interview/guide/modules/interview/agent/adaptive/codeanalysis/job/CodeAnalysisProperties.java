package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 代码分析配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.interview.code-analysis")
public class CodeAnalysisProperties {

  private boolean workerEnabled;
  private Duration retention = Duration.ofDays(30);
  private Duration timeout = Duration.ofMinutes(10);
  private int maxSnapshotBytes = 100 * 1024 * 1024;
  private int maxSnapshotFiles = 20_000;
  private long maxTokenCost = 200_000;
  private String workerToken;

  @AssertTrue(message = "启用代码分析 Worker 时必须配置 worker-token")
  public boolean isWorkerConfigurationValid() {
    return !workerEnabled || workerToken != null && !workerToken.isBlank();
  }
}
