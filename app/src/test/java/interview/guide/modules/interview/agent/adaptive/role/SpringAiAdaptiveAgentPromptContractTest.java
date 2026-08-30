package interview.guide.modules.interview.agent.adaptive.role;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.context;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SpringAiAdaptiveAgentPromptContractTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private ChatClient chatClient;
  @Mock
  private ChatClient.ChatClientRequestSpec requestSpec;
  @Mock
  private ChatClient.CallResponseSpec responseSpec;
  @Mock
  private AdaptiveAgentTelemetry telemetry;
  @Mock
  private AdaptiveInputTokenBudget inputTokenBudget;
  @Mock
  private AdaptiveModelOptionsFactory modelOptionsFactory;

  private SpringAiAdaptiveAgentModelGateway gateway;

  @BeforeEach
  void setUp() throws IOException {
    gateway = createGateway(telemetry);
    when(llmProviderRegistry.getPlainChatClient("provider-1")).thenReturn(chatClient);
    lenient().when(telemetry.observeTokenUsage(chatClient, "interviewer", "session-1"))
        .thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.options(any())).thenReturn(requestSpec);
    when(requestSpec.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(requestSpec);
    lenient().when(requestSpec.call()).thenReturn(responseSpec);
    when(modelOptionsFactory.interviewer(any())).thenReturn(OpenAiChatOptions.builder());
  }

  @Test
  @DisplayName("结构化响应映射为提问并声明可空来源字段协议")
  void shouldMapAskResponse() {
    respondWith("""
        {"type":"ASK","content":"Redis 缓存失效有哪些取舍？","reason":"继续验证工程权衡"}
        """);

    AgentAction action = gateway.nextAction(context(new CandidateAnswer(1, "候选人回答")));

    assertThat(action).isEqualTo(RespondAction.ask(
        "Redis 缓存失效有哪些取舍？",
        "继续验证工程权衡"
    ));
    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).system(systemPrompt.capture());
    verify(requestSpec).user(userPrompt.capture());
    assertThat(systemPrompt.getValue()).contains("JSON null", "禁止返回空字符串");
    assertThat(userPrompt.getValue()).contains(
        "<data-boundary>",
        "候选人回答",
        "专业基础",
        "\"working\"",
        "\"depthCeiling\"",
        "\"remainingFollowUps\""
    );
  }

  @Test
  @DisplayName("模型失败日志不包含候选人回答原文")
  void shouldNotLogCandidateAnswerWhenModelFails(CapturedOutput output) throws IOException {
    String sensitiveAnswer = "SENSITIVE-CANDIDATE-ANSWER-7F3A";
    when(requestSpec.call()).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "解析失败：" + sensitiveAnswer
    ));
    AdaptiveAgentTelemetry observableTelemetry = spy(
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry())
    );
    doReturn(chatClient).when(observableTelemetry)
        .observeTokenUsage(chatClient, "interviewer", "session-1");

    assertThatThrownBy(() -> createGateway(observableTelemetry).nextAction(
        context(new CandidateAnswer(1, sensitiveAnswer))
    )).isInstanceOf(BusinessException.class)
        .hasMessage("Agent interview model call failed");
    assertThat(output).doesNotContain(sensitiveAnswer);
  }

  private SpringAiAdaptiveAgentModelGateway createGateway(
      AdaptiveAgentTelemetry gatewayTelemetry
  ) throws IOException {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    ObjectMapper objectMapper = new ObjectMapper();
    return new SpringAiAdaptiveAgentModelGateway(
        llmProviderRegistry,
        objectMapper,
        gatewayTelemetry,
        inputTokenBudget,
        modelOptionsFactory,
        new AdaptiveAgentResponseMapper(objectMapper),
        properties,
        new PromptLoader(new DefaultResourceLoader())
    );
  }

  private void respondWith(String content) {
    when(responseSpec.chatResponse()).thenReturn(response(content));
  }
}
