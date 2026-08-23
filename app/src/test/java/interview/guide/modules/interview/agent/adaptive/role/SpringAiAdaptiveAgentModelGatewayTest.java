package interview.guide.modules.interview.agent.adaptive.role;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
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
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.acceptedObservation;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.context;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SpringAiAdaptiveAgentModelGatewayTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private ChatClient chatClient;
  @Mock
  private ChatClient.ChatClientRequestSpec requestSpec;
  @Mock
  private ChatClient.CallResponseSpec responseSpec;
  @Mock
  private ChatClient.StreamResponseSpec streamResponseSpec;
  @Mock
  private AdaptiveAgentTelemetry telemetry;
  @Mock
  private AdaptiveInputTokenBudget inputTokenBudget;
  @Mock
  private ToolGateway toolGateway;
  @Mock
  private AdaptiveModelOptionsFactory modelOptionsFactory;

  private SpringAiAdaptiveAgentModelGateway gateway;

  @BeforeEach
  void setUp() throws IOException {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    ObjectMapper objectMapper = new ObjectMapper();
    gateway = createGateway(properties, objectMapper, telemetry);
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
    when(toolGateway.callbacksFor(any())).thenReturn(List.of());
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
    assertThat(userPrompt.getValue()).contains("<data-boundary>", "候选人回答", "专业基础");
  }

  @Test
  @DisplayName("多问句输出原样通过，不做格式校验重写")
  void shouldAcceptMultiQuestionOutput() {
    String multiQuestion = """
        {"type":"ASK","content":"Redis 是什么？你为什么使用它？它有哪些好处？","reason":"继续"}
        """;
    when(responseSpec.chatResponse()).thenReturn(response(multiQuestion));

    assertThat(gateway.nextAction(context(new CandidateAnswer(1, "回答"))))
        .isEqualTo(RespondAction.ask("Redis 是什么？你为什么使用它？它有哪些好处？", "继续"));
    verify(responseSpec, times(1)).chatResponse();
  }

  @Test
  @DisplayName("重写后仍不合法时暴露明确校验错误")
  void shouldRejectInvalidResponseAfterRetry() {
    String invalid = """
        {"type":"ASK","content":"","reason":"继续"}
        """;
    when(responseSpec.chatResponse()).thenReturn(response(invalid), response(invalid));

    assertThatThrownBy(() -> gateway.nextAction(context(null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("incomplete");
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("模型忽略并行工具开关时只消费第一个工具")
  void shouldConsumeFirstParallelToolCall() {
    AssistantMessage parallelCalls = AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(
            toolCall("call-1", "question_bank_search"),
            toolCall("call-2", "rubric_lookup")
        ))
        .build();
    when(responseSpec.chatResponse()).thenReturn(response(parallelCalls));

    assertThat(gateway.nextAction(context(null)))
        .isInstanceOfSatisfying(ToolCallAction.class, toolCall ->
            assertThat(toolCall.toolName()).isEqualTo("question_bank_search")
        );
    verify(responseSpec, times(1)).chatResponse();
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
    SpringAiAdaptiveAgentModelGateway observableGateway = createGateway(
        new AdaptiveAgentProperties(),
        new ObjectMapper(),
        observableTelemetry
    );

    assertThatThrownBy(() -> observableGateway.nextAction(
        context(new CandidateAnswer(1, sensitiveAnswer))
    )).isInstanceOf(BusinessException.class)
        .hasMessage("Agent interview model call failed");
    assertThat(output).doesNotContain(sensitiveAnswer);
  }

  @Test
  @DisplayName("关闭 ChatClient 自动工具执行，tool call 交回手动 ReAct 循环")
  void shouldDisableAutoToolExecution() {
    respondWith("""
        {"type":"ASK","content":"Redis 缓存失效有哪些取舍？","reason":"继续验证"}
        """);

    gateway.nextAction(context(null));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor = ArgumentCaptor.forClass(
        Consumer.class
    );
    verify(requestSpec).advisors(captor.capture());
    ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
    captor.getValue().accept(advisorSpec);
    verify(advisorSpec).param(
        ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
        false
    );
  }

  @Test
  @DisplayName("首个工具成功后不再向 interviewer 注册工具")
  void shouldDisableInterviewerToolsAfterFirstAcceptedObservation() {
    ToolCallback callback = mock(ToolCallback.class);
    when(toolGateway.callbacksFor(any())).thenReturn(List.of(callback));
    respondWith("""
        {"type":"ASK","content":"Redis 缓存失效有哪些取舍？","reason":"继续验证"}
        """);

    gateway.nextAction(context(null));
    gateway.nextAction(context(null, List.of(acceptedObservation())));

    verify(modelOptionsFactory).interviewer(List.of(callback));
    verify(modelOptionsFactory).interviewer(List.of());
    verify(toolGateway, times(1)).callbacksFor(any());
  }

  @Test
  @DisplayName("流式决策把文本增量推给 deltaSink 并按完整文本解析动作")
  void shouldForwardDeltasAndMapStreamedText() {
    List<String> deltas = new java.util.ArrayList<>();
    when(requestSpec.stream()).thenReturn(streamResponseSpec);
    when(streamResponseSpec.chatResponse()).thenReturn(reactor.core.publisher.Flux.just(
        response("{\"type\":\"ASK\",\"content\":\"Redis 缓存失效"),
        response("有哪些取舍？\",\"reason\":\"继续验证\"}")
    ));

    AgentAction action = gateway.nextActionStreaming(
        context(new CandidateAnswer(1, "候选人回答")),
        deltas::add
    );

    assertThat(action).isEqualTo(RespondAction.ask("Redis 缓存失效有哪些取舍？", "继续验证"));
    assertThat(deltas).containsExactly(
        "{\"type\":\"ASK\",\"content\":\"Redis 缓存失效",
        "有哪些取舍？\",\"reason\":\"继续验证\"}"
    );
    verify(responseSpec, times(0)).chatResponse();
  }

  @Test
  @DisplayName("流式决策直接聚合工具调用且不发起第二次模型请求")
  void shouldAggregateStreamedToolCallWithoutSecondRequest() {
    List<String> deltas = new java.util.ArrayList<>();
    AssistantMessage toolCalls = AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(toolCall("call-1", "question_bank_search")))
        .build();
    when(requestSpec.stream()).thenReturn(streamResponseSpec);
    when(streamResponseSpec.chatResponse()).thenReturn(reactor.core.publisher.Flux.just(
        response(toolCalls)
    ));

    AgentAction action = gateway.nextActionStreaming(context(null), deltas::add);

    assertThat(action).isInstanceOfSatisfying(ToolCallAction.class, toolCall ->
        assertThat(toolCall.toolName()).isEqualTo("question_bank_search")
    );
    assertThat(deltas).isEmpty();
    verify(responseSpec, times(0)).chatResponse();
  }

  @Test
  @DisplayName("流式响应校验失败后保留一次流式重试")
  void shouldStreamRetryAfterRejectedResponse() {
    List<String> deltas = new java.util.ArrayList<>();
    String invalid = "{\"type\":\"ASK\",\"content\":\"\",\"reason\":\"继续\"}";
    String valid = "{\"type\":\"ASK\",\"content\":\"请介绍 Redis\",\"reason\":\"继续\"}";
    when(requestSpec.stream()).thenReturn(streamResponseSpec);
    when(streamResponseSpec.chatResponse()).thenReturn(
        reactor.core.publisher.Flux.just(response(invalid)),
        reactor.core.publisher.Flux.just(response(valid))
    );

    AgentAction action = gateway.nextActionStreaming(context(null), deltas::add);

    assertThat(action).isEqualTo(RespondAction.ask("请介绍 Redis", "继续"));
    assertThat(deltas).containsExactly(invalid, valid);
    verify(requestSpec, times(2)).stream();
    verify(responseSpec, times(0)).chatResponse();
  }

  private SpringAiAdaptiveAgentModelGateway createGateway(
      AdaptiveAgentProperties properties,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry gatewayTelemetry
  ) throws IOException {
    return new SpringAiAdaptiveAgentModelGateway(
        llmProviderRegistry,
        objectMapper,
        gatewayTelemetry,
        inputTokenBudget,
        new AgentRoleRegistry(properties),
        toolGateway,
        modelOptionsFactory,
        new AdaptiveAgentResponseMapper(objectMapper),
        properties,
        new PromptLoader(new DefaultResourceLoader())
    );
  }

  private void respondWith(String content) {
    when(responseSpec.chatResponse()).thenReturn(response(content));
  }

  private AssistantMessage.ToolCall toolCall(String id, String name) {
    return new AssistantMessage.ToolCall(id, "function", name, "{}");
  }
}
