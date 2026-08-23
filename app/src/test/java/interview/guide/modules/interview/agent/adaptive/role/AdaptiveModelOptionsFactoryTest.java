package interview.guide.modules.interview.agent.adaptive.role;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

@DisplayName("自适应 Agent 模型选项")
class AdaptiveModelOptionsFactoryTest {

  private static final int PLANNER_MAX_TOKENS = 321;
  private static final int INTERVIEWER_MAX_TOKENS = 123;
  private static final String REASONING_EFFORT = "low";

  private AdaptiveModelOptionsFactory factory;

  @BeforeEach
  void setUp() {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    properties.setPlannerMaxOutputTokens(PLANNER_MAX_TOKENS);
    properties.setInterviewerMaxOutputTokens(INTERVIEWER_MAX_TOKENS);
    properties.setReasoningEffort(REASONING_EFFORT);
    factory = new AdaptiveModelOptionsFactory(properties);
  }

  @Test
  @DisplayName("规划器应限制输出并使用低推理强度")
  void plannerShouldApplyBoundedOptions() {
    OpenAiChatOptions options = factory.planner().build();

    assertThat(options.getMaxTokens()).isEqualTo(PLANNER_MAX_TOKENS);
    assertThat(options.getReasoningEffort()).isEqualTo(REASONING_EFFORT);
    assertThat(options.getParallelToolCalls()).isNull();
  }

  @Test
  @DisplayName("面试官应限制输出并关闭并行工具调用")
  void interviewerShouldApplyBoundedOptions() {
    OpenAiChatOptions options = factory.interviewer(List.of()).build();

    assertThat(options.getMaxTokens()).isEqualTo(INTERVIEWER_MAX_TOKENS);
    assertThat(options.getReasoningEffort()).isEqualTo(REASONING_EFFORT);
    assertThat(options.getParallelToolCalls()).isFalse();
  }
}
