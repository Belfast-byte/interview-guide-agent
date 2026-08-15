package interview.guide.modules.interview.agent.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview.agent-loop")
public class AgentInterviewRuntimeProperties {

  private Duration deadline = Duration.ofSeconds(30);
  private Duration assessmentTimeout = Duration.ofSeconds(10);
  private Duration decisionTimeout = Duration.ofSeconds(20);
}
