package interview.guide.modules.interview.agent.adaptive.role;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SpringAiAdaptiveAgentModelGatewayTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;

  @Mock
  private StructuredOutputInvoker structuredOutputInvoker;

  @Mock
  private ChatClient chatClient;

  @Mock
  private AdaptiveAgentTelemetry telemetry;

  private SpringAiAdaptiveAgentModelGateway gateway;

  @BeforeEach
  void setUp() throws IOException {
    gateway = new SpringAiAdaptiveAgentModelGateway(
        llmProviderRegistry,
        structuredOutputInvoker,
        new ObjectMapper(),
        telemetry,
        new AdaptiveAgentProperties(),
        new DefaultResourceLoader()
    );
    when(llmProviderRegistry.getChatClientOrDefault("provider-1")).thenReturn(chatClient);
  }

  @Test
  @DisplayName("结构化响应映射为提问并使用显式数据边界")
  void shouldMapAskResponse() {
    when(invoke()).thenReturn(new SpringAiAdaptiveAgentModelGateway.AgentStepOutput(
        AgentResponseType.ASK,
        "Redis 缓存失效有哪些取舍？",
        "继续验证工程权衡"
    ));

    AgentAction action = gateway.nextAction(context(new CandidateAnswer(1, "候选人回答")));

    assertThat(action).isEqualTo(RespondAction.ask(
        "Redis 缓存失效有哪些取舍？",
        "继续验证工程权衡"
    ));
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(structuredOutputInvoker).invoke(
        eq(chatClient),
        anyString(),
        userPrompt.capture(),
        any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        anyString(),
        any(Logger.class)
    );
    assertThat(userPrompt.getValue()).contains("<data-boundary>", "候选人回答");
  }

  @Test
  @DisplayName("首次调用返回结束动作时快速失败")
  void shouldRejectFinishAsFirstAction() {
    when(invoke()).thenReturn(new SpringAiAdaptiveAgentModelGateway.AgentStepOutput(
        AgentResponseType.FINISH,
        "结束",
        "不应结束"
    ));

    assertThatThrownBy(() -> gateway.nextAction(context(null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("首次响应");
  }

  @Test
  @DisplayName("包含多个问题的模型响应被拒绝")
  void shouldRejectMultipleQuestions() {
    when(invoke()).thenReturn(new SpringAiAdaptiveAgentModelGateway.AgentStepOutput(
        AgentResponseType.ASK,
        "你用过 Redis 吗？为什么使用它？",
        "继续"
    ));

    assertThatThrownBy(() -> gateway.nextAction(context(new CandidateAnswer(1, "回答"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("一个单行问题");
  }

  @Test
  @DisplayName("模型失败日志不包含候选人回答原文")
  void shouldNotLogCandidateAnswerWhenModelFails(CapturedOutput output) throws IOException {
    String sensitiveAnswer = "SENSITIVE-CANDIDATE-ANSWER-7F3A";
    when(invoke()).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "解析失败：" + sensitiveAnswer
    ));
    SpringAiAdaptiveAgentModelGateway observableGateway =
        new SpringAiAdaptiveAgentModelGateway(
            llmProviderRegistry,
            structuredOutputInvoker,
            new ObjectMapper(),
            new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
            new AdaptiveAgentProperties(),
            new DefaultResourceLoader()
        );

    assertThatThrownBy(() -> observableGateway.nextAction(
        context(new CandidateAnswer(1, sensitiveAnswer))
    )).isInstanceOf(BusinessException.class)
        .hasMessage("Agent 面试决策失败");
    assertThat(output).doesNotContain(sensitiveAnswer);
  }

  private SpringAiAdaptiveAgentModelGateway.AgentStepOutput invoke() {
    return structuredOutputInvoker.invoke(
        eq(chatClient),
        anyString(),
        anyString(),
        org.mockito.ArgumentMatchers
            .<BeanOutputConverter<SpringAiAdaptiveAgentModelGateway.AgentStepOutput>>any(),
        eq(ErrorCode.AI_SERVICE_ERROR),
        anyString(),
        anyString(),
        any(Logger.class)
    );
  }

  private ReActModelContext context(CandidateAnswer answer) {
    return new ReActModelContext(
        new ReActRequest(
            "session-1",
            "provider-1",
            "JD",
            "Resume",
            6,
            List.of(),
            answer
        ),
        List.of()
    );
  }
}
