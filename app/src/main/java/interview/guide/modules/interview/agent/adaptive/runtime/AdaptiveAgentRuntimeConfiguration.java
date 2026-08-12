package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdaptiveAgentRuntimeConfiguration {

  @Bean
  BoundedReActRuntime boundedReActRuntime(AgentModelGateway modelGateway) {
    return new BoundedReActRuntime(modelGateway, action -> {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "M0 尚未启用 Agent 工具");
    });
  }
}
