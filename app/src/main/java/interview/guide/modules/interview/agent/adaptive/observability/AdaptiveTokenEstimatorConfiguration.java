package interview.guide.modules.interview.agent.adaptive.observability;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自适应面试共用的确定性 Token 估算器。
 */
@Configuration(proxyBeanMethods = false)
public class AdaptiveTokenEstimatorConfiguration {

  @Bean
  TokenCountEstimator adaptiveTokenCountEstimator() {
    return new JTokkitTokenCountEstimator(EncodingType.CL100K_BASE);
  }
}
