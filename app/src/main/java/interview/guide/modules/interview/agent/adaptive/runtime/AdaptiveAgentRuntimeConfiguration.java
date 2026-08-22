package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自适应 Agent 运行时 Spring 配置，装配模型网关、工具执行器和有界 ReAct 运行时。
 */
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
