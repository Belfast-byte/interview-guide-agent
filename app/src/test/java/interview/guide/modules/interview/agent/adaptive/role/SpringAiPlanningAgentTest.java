package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlanningRequest;
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
class SpringAiPlanningAgentTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;

  @Mock
  private StructuredOutputInvoker structuredOutputInvoker;

  @Mock
  private AdaptiveAgentTelemetry telemetry;

  @Mock
  private ChatClient chatClient;

  private SpringAiPlanningAgent planningAgent;

  @BeforeEach
  void setUp() throws IOException {
    planningAgent = new SpringAiPlanningAgent(
        llmProviderRegistry,
        structuredOutputInvoker,
        new ObjectMapper(),
        telemetry,
        new AdaptiveAgentProperties(),
        new DefaultResourceLoader()
    );
    when(llmProviderRegistry.getChatClientOrDefault("provider-1"))
        .thenReturn(chatClient);
  }

  @Test
  @DisplayName("结构化规划保持维度顺序并使用显式数据边界")
  void shouldReturnOrderedPlanProposal() {
    PlanProposal expected = new PlanProposal(List.of(
        dimension("专业基础", "缓存与并发"),
        dimension("项目经验", "架构取舍")
    ));
    when(invoke()).thenReturn(expected);

    PlanProposal actual = planningAgent.propose(request(), "provider-1");

    assertThat(actual).isSameAs(expected);
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker).invoke(
        eq(chatClient),
        anyString(),
        userPrompt.capture(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_agent_planning"),
        any(Logger.class)
    );
    assertThat(userPrompt.getValue())
        .contains("<data-boundary>", "后端工程师", "候选人项目经历");
    verify(telemetry).modelCallSucceeded(eq("planner"), eq("PLAN"), anyLong());
  }

  @Test
  @DisplayName("空维度规划在模型边界快速失败")
  void shouldRejectEmptyDimensions() {
    when(invoke()).thenReturn(new PlanProposal(List.of()));

    assertThatThrownBy(() -> planningAgent.propose(request(), "provider-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("1 到 12");
  }

  @Test
  @DisplayName("重复维度规划在模型边界快速失败")
  void shouldRejectDuplicateDimensions() {
    when(invoke()).thenReturn(new PlanProposal(List.of(
        dimension("专业基础", "缓存"),
        dimension(" 专业基础 ", "并发")
    )));

    assertThatThrownBy(() -> planningAgent.propose(request(), "provider-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("重复维度");
  }

  @Test
  @DisplayName("底层模型失败不向调用方暴露解析内容")
  void shouldSanitizeModelFailure() {
    when(invoke()).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "底层解析内容"
    ));

    assertThatThrownBy(() -> planningAgent.propose(request(), "provider-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Agent 规划失败");
  }

  private PlanProposal invoke() {
    return structuredOutputInvoker.invoke(
        eq(chatClient),
        anyString(),
        anyString(),
        ArgumentMatchers.<BeanOutputConverter<PlanProposal>>any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        eq("adaptive_agent_planning"),
        any(Logger.class)
    );
  }

  private PlanningRequest request() {
    return new PlanningRequest(
        "session-1",
        "后端工程师，要求 Java 和 Redis",
        "候选人项目经历"
    );
  }

  private DimensionProposal dimension(String name, String focus) {
    return new DimensionProposal(name, focus, 2, List.of(), null);
  }
}
