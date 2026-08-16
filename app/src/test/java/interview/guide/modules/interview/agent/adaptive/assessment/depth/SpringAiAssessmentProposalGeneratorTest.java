package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiAssessmentProposalGeneratorTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;

  @Mock
  private StructuredOutputInvoker structuredOutputInvoker;

  @Mock
  private AdaptiveAgentTelemetry telemetry;

  @Mock
  private AdaptiveInputTokenBudget inputTokenBudget;

  @Mock
  private ChatClient chatClient;

  private SpringAiAssessmentProposalGenerator generator;

  @BeforeEach
  void setUp() throws IOException {
    generator = new SpringAiAssessmentProposalGenerator(
        llmProviderRegistry,
        structuredOutputInvoker,
        new ObjectMapper(),
        telemetry,
        inputTokenBudget,
        new DeadlineExecutor(),
        new AdaptiveAgentProperties(),
        new DefaultResourceLoader()
    );
    when(llmProviderRegistry.getChatClientOrDefault("provider-1"))
        .thenReturn(chatClient);
    when(telemetry.observeTokenUsage(
        eq(chatClient),
        eq("depth_assessor"),
        anyString()
    ))
        .thenReturn(chatClient);
  }

  @Test
  @DisplayName("评估 Prompt 只序列化当前回答上下文而不暴露会话身份")
  void shouldSerializeOnlyAssessmentContext() {
    AssessmentProposal expected = new AssessmentProposal(
        DepthLevel.L3,
        0.8,
        "说明了权衡",
        false,
        List.of("重要数据使用版本号")
    );
    when(invoke()).thenReturn(expected);

    AssessmentProposal actual = generator.generate(request(), "provider-1");

    assertThat(actual).isSameAs(expected);
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker).invoke(
        eq(chatClient),
        anyString(),
        userPrompt.capture(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_depth_assessment"),
        any(Logger.class)
    );
    assertThat(userPrompt.getValue())
        .contains("如何保证缓存一致性", "重要数据使用版本号", "L0", "L4")
        .doesNotContain("private-session-id");
  }

  @Test
  @DisplayName("评估 system prompt 注入 Skill 知识基线和 few-shot 校准示例")
  void shouldIncludeSkillBaselineAndCalibrationExamplesInSystemPrompt() {
    AssessmentProposal expected = new AssessmentProposal(
        DepthLevel.L2,
        0.8,
        "说明了权衡",
        false,
        List.of("重要数据使用版本号")
    );
    when(invoke()).thenReturn(expected);
    AssessmentRequest request = new AssessmentRequest(
        "private-session-id",
        1,
        AssessmentContext.currentAnswer(
            "专业基础",
            "缓存一致性",
            "如何保证缓存一致性？",
            "延迟双删只能降低概率，重要数据使用版本号。"
        ),
        "### Redis (REDIS)\n- 缓存穿透：布隆过滤器 / 空值缓存"
    );

    generator.generate(request, "provider-1");

    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker).invoke(
        eq(chatClient),
        systemPrompt.capture(),
        anyString(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_depth_assessment"),
        any(Logger.class)
    );
    assertThat(systemPrompt.getValue())
        .contains("评估 Agent 校准示例", "用布隆过滤器就行")
        .contains("### Redis (REDIS)", "缓存穿透")
        .doesNotContain("private-session-id");
  }

  @Test
  @DisplayName("相同回答在不同会话和轮次下生成一致的评估输入")
  void shouldBuildSameAssessmentInputAcrossSessions() {
    AssessmentProposal expected = new AssessmentProposal(
        DepthLevel.L3,
        0.8,
        "说明了权衡",
        false,
        List.of("重要数据使用版本号")
    );
    when(invoke()).thenReturn(expected);

    generator.generate(request("session-with-high-history", 8), "provider-1");
    generator.generate(request("session-with-low-history", 3), "provider-1");

    ArgumentCaptor<String> userPrompts = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker, times(2)).invoke(
        eq(chatClient),
        anyString(),
        userPrompts.capture(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_depth_assessment"),
        any(Logger.class)
    );
    assertThat(userPrompts.getAllValues())
        .hasSize(2)
        .allSatisfy(prompt -> assertThat(prompt)
            .isEqualTo(userPrompts.getAllValues().getFirst()));
  }

  private AssessmentProposal invoke() {
    return structuredOutputInvoker.invoke(
        eq(chatClient),
        anyString(),
        anyString(),
        ArgumentMatchers.<BeanOutputConverter<AssessmentProposal>>any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_depth_assessment"),
        any(Logger.class)
    );
  }

  private AssessmentRequest request() {
    return request("private-session-id", 1);
  }

  private AssessmentRequest request(String sessionId, int turnIndex) {
    return new AssessmentRequest(
        sessionId,
        turnIndex,
        AssessmentContext.currentAnswer(
            "专业基础",
            "缓存一致性",
            "如何保证缓存一致性？",
            "延迟双删只能降低概率，重要数据使用版本号。"
        )
    );
  }
}
