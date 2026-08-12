package interview.guide.modules.interview.agent.adaptive.application;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.interview.adaptive-agent")
public class AdaptiveAgentProperties {

  private boolean enabled;
  private int maxSteps = 4;
  private int maxToolCalls;
  private Duration deadline = Duration.ofSeconds(30);
  private Duration plannerDeadline = Duration.ofSeconds(30);
  private String systemPromptPath =
      "classpath:prompts/adaptive-agent-interviewer-system.st";
  private String userPromptPath =
      "classpath:prompts/adaptive-agent-interviewer-user.st";
  private String plannerSystemPromptPath =
      "classpath:prompts/adaptive-agent-planner-system.st";
  private String plannerUserPromptPath =
      "classpath:prompts/adaptive-agent-planner-user.st";
}
