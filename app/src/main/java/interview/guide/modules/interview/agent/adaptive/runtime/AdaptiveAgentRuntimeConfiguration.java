package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自适应 Agent 运行时 Spring 配置。
 */
@Configuration(proxyBeanMethods = false)
public class AdaptiveAgentRuntimeConfiguration {

  @Bean
  WorkingMemoryValidator workingMemoryValidator() {
    return new WorkingMemoryValidator();
  }

  @Bean
  BoundedActionRuntime boundedActionRuntime(
      AgentModelGateway modelGateway,
      DeadlineExecutor deadlineExecutor
  ) {
    return new BoundedActionRuntime(modelGateway, deadlineExecutor);
  }

  @Bean
  InterviewAgentLoop interviewAgentLoop(
      InterviewDecisionModel model,
      AgentDecisionValidator validator,
      DeadlineExecutor deadlineExecutor
  ) {
    return new InterviewAgentLoop(model, validator, deadlineExecutor);
  }
}
