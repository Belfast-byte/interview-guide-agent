package interview.guide.modules.interview.agent.adaptive.application;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自适应 Agent 配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.interview.adaptive-agent")
public class AdaptiveAgentProperties {

  private int maxSteps = 4;
  private int maxToolCalls = 2;
  private Duration deadline = Duration.ofSeconds(30);
  private Duration plannerDeadline = Duration.ofSeconds(30);
  private Duration briefDeadline = Duration.ofSeconds(20);
  private Duration claimDeadline = Duration.ofSeconds(20);
  private Duration assessmentDeadline = Duration.ofSeconds(20);
  private int maxInputTokens = 12_000;
  private int plannerMaxOutputTokens = 2_048;
  private int interviewerMaxOutputTokens = 1_024;
  private int structuredMaxOutputTokens = 2_048;
  private String reasoningEffort = "low";
  private String systemPromptPath =
      "classpath:prompts/adaptive-agent-interviewer-system.st";
  private String userPromptPath =
      "classpath:prompts/adaptive-agent-interviewer-user.st";
  private String plannerSystemPromptPath =
      "classpath:prompts/adaptive-agent-planner-system.st";
  private String plannerUserPromptPath =
      "classpath:prompts/adaptive-agent-planner-user.st";
  private String briefSystemPromptPath =
      "classpath:prompts/adaptive-agent-dimension-brief-system.st";
  private String briefUserPromptPath =
      "classpath:prompts/adaptive-agent-dimension-brief-user.st";
  private String claimSystemPromptPath =
      "classpath:prompts/adaptive-agent-claim-extraction-system.st";
  private String claimUserPromptPath =
      "classpath:prompts/adaptive-agent-claim-extraction-user.st";
  private String assessmentSystemPromptPath =
      "classpath:prompts/adaptive-agent-assessment-system.st";
  private String assessmentUserPromptPath =
      "classpath:prompts/adaptive-agent-assessment-user.st";
  private String assessmentExamplesPath =
      "classpath:prompts/adaptive-agent-assessment-agents.md";
}
