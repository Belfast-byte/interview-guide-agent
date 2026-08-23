package interview.guide.modules.interview.agent.adaptive.observability;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

/**
 * 输入 Token 预算控制器，防止单轮上下文超限。
 */
@Component
public class AdaptiveInputTokenBudget {

  private final AdaptiveAgentProperties properties;
  private final AdaptiveAgentTelemetry telemetry;
  private final TokenCountEstimator estimator;

  public AdaptiveInputTokenBudget(
      AdaptiveAgentProperties properties,
      AdaptiveAgentTelemetry telemetry,
      TokenCountEstimator estimator
  ) {
    this.properties = properties;
    this.telemetry = telemetry;
    this.estimator = estimator;
  }

  public void verify(String role, String systemPrompt, String userPrompt) {
    int tokens = estimator.estimate(systemPrompt + "\n" + userPrompt);
    telemetry.inputTokens(role, tokens);
    if (tokens > properties.getMaxInputTokens()) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          role + " 输入超过 token 上限"
      );
    }
  }
}
