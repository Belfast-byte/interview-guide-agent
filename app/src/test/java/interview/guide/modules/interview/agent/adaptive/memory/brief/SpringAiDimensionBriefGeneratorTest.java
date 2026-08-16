package interview.guide.modules.interview.agent.adaptive.memory.brief;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiDimensionBriefGeneratorTest {

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

  private SpringAiDimensionBriefGenerator generator;

  @BeforeEach
  void setUp() throws IOException {
    generator = new SpringAiDimensionBriefGenerator(
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
        chatClient,
        "memory_summarizer",
        "session-1"
    ))
        .thenReturn(chatClient);
  }

  @Test
  @DisplayName("维度小结使用独立结构化调用并保留数据边界")
  void shouldGenerateStructuredBrief() {
    DimensionBriefProposal expected = new DimensionBriefProposal(
        "讨论了缓存一致性的方案与取舍",
        List.of(1, 2)
    );
    when(invoke()).thenReturn(expected);

    DimensionBriefProposal actual = generator.generate(request(), "provider-1");

    assertThat(actual).isSameAs(expected);
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker).invoke(
        eq(chatClient),
        anyString(),
        userPrompt.capture(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_dimension_brief"),
        any(Logger.class)
    );
    assertThat(userPrompt.getValue()).contains(
        "<data-boundary>",
        "缓存一致性",
        "候选人完整回答"
    );
    verify(telemetry).modelCallSucceeded(
        eq("memory_summarizer"),
        eq("BRIEF"),
        anyLong()
    );
  }

  @Test
  @DisplayName("维度小结模型失败时不暴露底层解析内容")
  void shouldSanitizeModelFailure() {
    when(invoke()).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "底层解析内容"
    ));

    assertThatThrownBy(() -> generator.generate(request(), "provider-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("维度小结生成失败");
  }

  private DimensionBriefProposal invoke() {
    return structuredOutputInvoker.invoke(
        eq(chatClient),
        anyString(),
        anyString(),
        ArgumentMatchers.<BeanOutputConverter<DimensionBriefProposal>>any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_dimension_brief"),
        any(Logger.class)
    );
  }

  private DimensionBriefRequest request() {
    return new DimensionBriefRequest(
        "session-1",
        0,
        "专业基础",
        "缓存一致性",
        List.of(
            new DimensionBriefTurn(1, "问题一", "回答一"),
            new DimensionBriefTurn(2, "问题二", "候选人完整回答")
        )
    );
  }
}
