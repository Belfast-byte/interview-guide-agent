package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptiveInputTokenBudgetTest {

  @Test
  @DisplayName("完整 Prompt 超过配置 token 上限时快速失败并记录输入分布")
  void shouldRejectPromptOverConfiguredTokenLimit() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    properties.setMaxInputTokens(10);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TokenCountEstimator estimator = mock(TokenCountEstimator.class);
    when(estimator.estimate(anyString())).thenReturn(11);
    AdaptiveInputTokenBudget budget = new AdaptiveInputTokenBudget(
        properties,
        new AdaptiveAgentTelemetry(meterRegistry),
        estimator
    );

    assertThatThrownBy(() -> budget.verify("planner", "123456", "12345"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("planner 输入超过 token 上限");
    assertThat(meterRegistry
        .get(AdaptiveAgentTelemetry.MODEL_INPUT_TOKENS)
        .tag("role", "planner")
        .summary()
        .totalAmount())
        .isEqualTo(11);
  }

  @Test
  @DisplayName("输入未超过 token 上限时允许继续")
  void shouldAllowPromptWithinConfiguredTokenLimit() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    properties.setMaxInputTokens(10);
    TokenCountEstimator estimator = mock(TokenCountEstimator.class);
    when(estimator.estimate(anyString())).thenReturn(10);
    AdaptiveInputTokenBudget budget = new AdaptiveInputTokenBudget(
        properties,
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        estimator
    );

    budget.verify("interviewer", "1234", "12345");
  }
}
