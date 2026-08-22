package interview.guide.modules.interview.agent.adaptive.role;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.ProjectInterviewContext;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolObservation;
import interview.guide.modules.interview.agent.adaptive.tool.ToolGateway;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

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
  private AdaptiveAgentTelemetry telemetry;

  @Mock
  private AdaptiveInputTokenBudget inputTokenBudget;

  @Mock
  private ToolGateway toolGateway;

  private SpringAiAdaptiveAgentModelGateway gateway;

  @BeforeEach
  void setUp() throws IOException {
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    gateway = new SpringAiAdaptiveAgentModelGateway(
        llmProviderRegistry,
        new ObjectMapper(),
        telemetry,
        inputTokenBudget,
        new AgentRoleRegistry(properties),
        toolGateway,
        properties,
        new PromptLoader(new DefaultResourceLoader())
    );
    when(llmProviderRegistry.getPlainChatClient("provider-1")).thenReturn(chatClient);
    lenient().when(telemetry.observeTokenUsage(
        chatClient,
        "interviewer",
        "session-1"
    ))
        .thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.options(any())).thenReturn(requestSpec);
    when(requestSpec.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
        .thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(toolGateway.callbacksFor(any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("结构化响应映射为提问并使用显式数据边界")
  void shouldMapAskResponse() {
    respondWith("""
        {"type":"ASK","content":"Redis 缓存失效有哪些取舍？","reason":"继续验证工程权衡"}
        """);

    AgentAction action = gateway.nextAction(context(new CandidateAnswer(1, "候选人回答")));

    assertThat(action).isEqualTo(RespondAction.ask(
        "Redis 缓存失效有哪些取舍？",
        "继续验证工程权衡"
    ));
    ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userPrompt.capture());
    assertThat(userPrompt.getValue()).contains(
        "<data-boundary>",
        "候选人回答",
        "专业基础",
        "缓存与并发",
        "question_bank_search"
    );
  }

  @Test
  @DisplayName("原生 function call 映射为工具动作但不由框架自动执行")
  void shouldMapNativeToolCall() {
    AssistantMessage message = AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(new AssistantMessage.ToolCall(
            "call-1",
            "function",
            "question_bank_search",
            "{\"query\":\"Redis\"}"
        )))
        .build();
    when(responseSpec.chatResponse()).thenReturn(response(message));

    AgentAction action = gateway.nextAction(context(null));

    assertThat(action).isInstanceOfSatisfying(ToolCallAction.class, toolCall -> {
      assertThat(toolCall.toolName()).isEqualTo("question_bank_search");
      assertThat(toolCall.arguments()).isEqualTo(Map.of("query", "Redis"));
    });
  }

  @Test
  @DisplayName("审核题来源必须与已接受的题库结果逐字段一致")
  void shouldAcceptVerifiedQuestionProvenance() {
    respondWith("""
        {
          "type":"ASK",
          "content":"Redis 为什么需要过期策略？",
          "reason":"采用审核题",
          "sourceQuestionId":"question:42",
          "sourceDifficulty":"MEDIUM"
        }
        """);
    ReActModelContext context = context(
        null,
        List.of(new ToolObservation(
            "question_bank_search",
            Map.of("query", "Redis"),
            true,
            "question-search:42",
            """
                [{"stableId":"question:42","id":42,"category":"Redis",\
                "difficulty":"MEDIUM","question":"Redis 为什么需要过期策略？"}]
                """
        ))
    );

    assertThat(gateway.nextAction(context)).isEqualTo(RespondAction.ask(
        "Redis 为什么需要过期策略？",
        "采用审核题",
        new QuestionProvenance("question:42", "MEDIUM")
    ));
  }

  @Test
  @DisplayName("模型伪造或改写题库来源时注入拒绝原因重写，重写仍不匹配才快速失败")
  void shouldRejectUnverifiedQuestionProvenanceAfterRetry() {
    String invalid = """
        {
          "type":"ASK",
          "content":"被改写的问题？",
          "reason":"声称采用审核题",
          "sourceQuestionId":"question:42",
          "sourceDifficulty":"MEDIUM"
        }
        """;
    when(responseSpec.chatResponse()).thenReturn(
        response(new AssistantMessage(invalid)),
        response(new AssistantMessage(invalid))
    );
    ReActModelContext context = context(
        null,
        List.of(new ToolObservation(
            "question_bank_search",
            Map.of("query", "Redis"),
            true,
            "question-search:42",
            """
                [{"stableId":"question:42","id":42,"category":"Redis",\
                "difficulty":"MEDIUM","question":"原始审核问题？"}]
                """
        ))
    );

    assertThatThrownBy(() -> gateway.nextAction(context))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("does not match");
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("问题格式违规时注入拒绝原因重写，重写成功后正常出题")
  void shouldRegenerateAfterQuestionFormatViolation() {
    String invalid = """
        {"type":"ASK","content":"你用过 Redis 吗？为什么使用它？","reason":"继续"}
        """;
    String corrected = """
        {"type":"ASK","content":"你在什么场景下使用 Redis？","reason":"继续验证"}
        """;
    when(responseSpec.chatResponse()).thenReturn(
        response(new AssistantMessage(invalid)),
        response(new AssistantMessage(corrected))
    );

    AgentAction action = gateway.nextAction(context(new CandidateAnswer(1, "回答")));

    assertThat(action).isEqualTo(RespondAction.ask("你在什么场景下使用 Redis？", "继续验证"));
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("项目问题来源必须匹配已校验的场景锚点")
  void shouldAcceptVerifiedCodeQuestionProvenance() {
    respondWith("""
        {
          "type":"ASK",
          "content":"这个缓存失效实现在哪些并发条件下会失效？",
          "reason":"基于真实代码场景追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"order/OrderCache.java:42",
          "codeFactUsage":"QUESTION_SOURCE"
        }
        """);

    assertThat(gateway.nextAction(contextWithProject())).isEqualTo(
        RespondAction.askFromCode(
            "这个缓存失效实现在哪些并发条件下会失效？",
            "基于真实代码场景追问",
            new CodeQuestionProvenance(
                "scenario-1",
                "order/OrderCache.java:42",
                CodeFactUsage.QUESTION_SOURCE
            )
        )
    );
  }

  @Test
  @DisplayName("项目问题伪造代码行号时注入拒绝原因并重新出题")
  void shouldRegenerateQuestionAfterInventedCodeAnchor() {
    String invalid = """
        {
          "type":"ASK",
          "content":"这个缓存实现为什么这样设计？",
          "reason":"基于代码追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"invented/File.java:99",
          "codeFactUsage":"QUESTION_SOURCE"
        }
        """;
    String corrected = """
        {
          "type":"ASK",
          "content":"这个缓存实现为什么这样设计？",
          "reason":"基于代码追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"order/OrderCache.java:42",
          "codeFactUsage":"QUESTION_SOURCE"
        }
        """;
    when(responseSpec.chatResponse()).thenReturn(
        response(new AssistantMessage(invalid)),
        response(new AssistantMessage(corrected))
    );

    assertThat(gateway.nextAction(contextWithProject()))
        .isEqualTo(RespondAction.askFromCode(
            "这个缓存实现为什么这样设计？",
            "基于代码追问",
            new CodeQuestionProvenance(
                "scenario-1",
                "order/OrderCache.java:42",
                CodeFactUsage.QUESTION_SOURCE
            )
        ));
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("模型两次返回结束动作时才快速失败")
  void shouldRejectFinishAfterRetry() {
    String finish = """
        {"type":"FINISH","content":"结束","reason":"不应结束"}
        """;
    when(responseSpec.chatResponse()).thenReturn(
        response(new AssistantMessage(finish)),
        response(new AssistantMessage(finish))
    );

    assertThatThrownBy(() -> gateway.nextAction(context(null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("planned turns");
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("模型两次返回多个问题时才快速失败")
  void shouldRejectMultipleQuestionsAfterRetry() {
    String invalid = """
        {"type":"ASK","content":"你用过 Redis 吗？为什么使用它？","reason":"继续"}
        """;
    when(responseSpec.chatResponse()).thenReturn(
        response(new AssistantMessage(invalid)),
        response(new AssistantMessage(invalid))
    );

    assertThatThrownBy(() -> gateway.nextAction(
        context(new CandidateAnswer(1, "回答"))
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("one single-line question");
    verify(responseSpec, times(2)).chatResponse();
  }

  @Test
  @DisplayName("模型失败日志不包含候选人回答原文")
  void shouldNotLogCandidateAnswerWhenModelFails(CapturedOutput output) throws IOException {
    String sensitiveAnswer = "SENSITIVE-CANDIDATE-ANSWER-7F3A";
    when(requestSpec.call()).thenThrow(new BusinessException(
        ErrorCode.AI_SERVICE_ERROR,
        "解析失败：" + sensitiveAnswer
    ));
    AdaptiveAgentProperties properties = new AdaptiveAgentProperties();
    AdaptiveAgentTelemetry observableTelemetry = spy(
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry())
    );
    doReturn(chatClient).when(observableTelemetry)
        .observeTokenUsage(chatClient, "interviewer", "session-1");
    SpringAiAdaptiveAgentModelGateway observableGateway =
        new SpringAiAdaptiveAgentModelGateway(
            llmProviderRegistry,
            new ObjectMapper(),
            observableTelemetry,
            inputTokenBudget,
            new AgentRoleRegistry(properties),
            toolGateway,
            properties,
            new PromptLoader(new DefaultResourceLoader())
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
    ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorsCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(requestSpec).advisors(advisorsCaptor.capture());
    ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
    advisorsCaptor.getValue().accept(advisorSpec);
    verify(advisorSpec).param(
        ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
        false
    );
  }

  private void respondWith(String content) {
    when(responseSpec.chatResponse()).thenReturn(response(new AssistantMessage(content)));
  }

  private ChatResponse response(AssistantMessage message) {
    return new ChatResponse(List.of(new Generation(message)));
  }

  private ReActModelContext context(CandidateAnswer answer) {
    return context(answer, List.of());
  }

  private ReActModelContext context(
      CandidateAnswer answer,
      List<ToolObservation> observations
  ) {
    return new ReActModelContext(
        new ReActRequest(
            "session-1",
            AgentRole.INTERVIEWER,
            "provider-1",
            new InterviewerContext(
                "JD",
                "Resume",
                answer == null ? 0 : 1,
                6,
                0,
                "专业基础",
                "缓存与并发",
                List.of("question_bank_search"),
                null,
                List.of(),
                answer,
                List.of(),
                List.of(),
                null,
                null,
                null
            )
        ),
        observations
    );
  }

  private ReActModelContext contextWithProject() {
    ProjectInterviewContext project = new ProjectInterviewContext(
        "digest-1",
        List.of(),
        List.of(new ProjectInterviewContext.ProjectScenario(
            "scenario-1",
            "缓存失效并发场景",
            "订单缓存存在版本号失效逻辑",
            "order/OrderCache.java:42",
            "EXPLAIN",
            "解释并发边界",
            null
        ))
    );
    return new ReActModelContext(
        new ReActRequest(
            "session-1",
            AgentRole.INTERVIEWER,
            "provider-1",
            new InterviewerContext(
                "JD",
                "Resume",
                1,
                6,
                0,
                "项目经验",
                "架构取舍",
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                null,
                project
            )
        ),
        List.of()
    );
  }
}
