package interview.guide.modules.interview.agent.adaptive.algorithm;

import lombok.Data;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview.algorithm")
public class AlgorithmInterviewProperties {

  private int maxExecutionsPerSession = 20;
  private int maxSourceBytes = 64 * 1024;
  private String sandboxBaseUrl = "http://sandboxd:8090";
  private Duration sandboxConnectTimeout = Duration.ofSeconds(2);
  private Duration sandboxReadTimeout = Duration.ofSeconds(15);
}
