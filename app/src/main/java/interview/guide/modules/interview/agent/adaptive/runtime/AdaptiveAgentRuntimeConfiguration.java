package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdaptiveAgentRuntimeConfiguration {

  @Bean
  BoundedReActRuntime boundedReActRuntime(
      AgentModelGateway modelGateway,
      ToolGateway toolGateway,
      DeadlineExecutor deadlineExecutor
  ) {
    return new BoundedReActRuntime(modelGateway, toolGateway, deadlineExecutor);
  }
}
