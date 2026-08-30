package interview.guide.modules.interview.agent.adaptive.role;

import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActModelContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import tools.jackson.databind.ObjectMapper;

import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.context;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.contextAtTurn;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.contextWithProject;
import static interview.guide.modules.interview.agent.adaptive.role.AdaptiveAgentRoleTestFixtures.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveAgentResponseMapperTest {

  private AdaptiveAgentResponseMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new AdaptiveAgentResponseMapper(new ObjectMapper());
  }

  @Test
  @DisplayName("空字符串来源字段在模型边界规范化为无来源")
  void shouldNormalizeBlankProvenanceFields() {
    String output = """
        {
          "type":"ASK",
          "content":"Redis 缓存失效有哪些取舍？",
          "reason":"现场生成问题",
          "sourceQuestionId":"",
          "sourceDifficulty":" ",
          "codeSourceId":"",
          "codeAnchor":" ",
          "codeFactUsage":""
        }
        """;

    AgentAction action = mapper.map(response(output), context(null));

    assertThat(action).isEqualTo(RespondAction.ask(
        "Redis 缓存失效有哪些取舍？",
        "现场生成问题"
    ));
  }

  @Test
  @DisplayName("非法代码事实用途成为可纠正的模型输出拒绝")
  void shouldRejectInvalidCodeFactUsageExplicitly() {
    String output = """
        {
          "type":"ASK",
          "content":"这个实现有哪些并发风险？",
          "reason":"基于代码追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"order/OrderCache.java:42",
          "codeFactUsage":"INVALID"
        }
        """;

    assertThatThrownBy(() -> mapper.map(response(output), contextWithProject()))
        .isInstanceOf(AdaptiveAgentResponseMapper.ModelOutputRejectionException.class)
        .hasMessage("Code fact usage is invalid");
  }

  @Test
  @DisplayName("原生 function call 映射为工具动作")
  void shouldMapNativeToolCall() {
    AssistantMessage message = AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(new AssistantMessage.ToolCall(
            "call-1",
            "function",
            "rubric_search",
            "{\"query\":\"Redis\"}"
        )))
        .build();

    AgentAction action = mapper.map(response(message), context(null));

    assertThat(action).isInstanceOfSatisfying(ToolCallAction.class, toolCall -> {
      assertThat(toolCall.toolName()).isEqualTo("rubric_search");
      assertThat(toolCall.arguments()).isEqualTo(Map.of("query", "Redis"));
    });
  }

  @Test
  @DisplayName("原生多工具调用只消费第一个")
  void shouldMapOnlyFirstNativeToolCall() {
    AssistantMessage message = AssistantMessage.builder()
        .content("")
        .toolCalls(List.of(
            new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "rubric_search",
                "{\"query\":\"Redis\"}"
            ),
            new AssistantMessage.ToolCall(
                "call-2",
                "function",
                "code.trace",
                "{\"sourceId\":\"artifact-1\"}"
            )
        ))
        .build();

    AgentAction action = mapper.map(response(message), context(null));

    assertThat(action).isInstanceOfSatisfying(ToolCallAction.class, toolCall -> {
      assertThat(toolCall.toolName()).isEqualTo("rubric_search");
      assertThat(toolCall.arguments()).isEqualTo(Map.of("query", "Redis"));
    });
  }

  @Test
  @DisplayName("旧题库来源字段不再被旧运行时接受")
  void shouldRejectLegacyQuestionBankProvenance() {
    String output = """
        {
          "type":"ASK",
          "content":"Redis 为什么需要过期策略？",
          "reason":"采用审核题",
          "sourceQuestionId":"question:42",
          "sourceDifficulty":"MEDIUM"
        }
        """;

    assertThatThrownBy(() -> mapper.map(response(output), context(null)))
        .isInstanceOf(AdaptiveAgentResponseMapper.ModelOutputRejectionException.class)
        .hasMessage("Question bank provenance is no longer supported");
  }

  @Test
  @DisplayName("项目问题来源必须匹配已校验的场景锚点")
  void shouldAcceptVerifiedCodeQuestionProvenance() {
    String output = """
        {
          "type":"ASK",
          "content":"这个缓存失效实现在哪些并发条件下会失效？",
          "reason":"基于真实代码场景追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"order/OrderCache.java:42",
          "codeFactUsage":"QUESTION_SOURCE"
        }
        """;

    assertThat(mapper.map(response(output), contextWithProject())).isEqualTo(
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
  @DisplayName("伪造代码锚点成为可纠正的模型输出拒绝")
  void shouldRejectInventedCodeAnchorExplicitly() {
    String output = """
        {
          "type":"ASK",
          "content":"这个缓存实现为什么这样设计？",
          "reason":"基于代码追问",
          "codeSourceId":"scenario-1",
          "codeAnchor":"invented/File.java:99",
          "codeFactUsage":"QUESTION_SOURCE"
        }
        """;

    assertThatThrownBy(() -> mapper.map(response(output), contextWithProject()))
        .isInstanceOf(AdaptiveAgentResponseMapper.ModelOutputRejectionException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  @DisplayName("Interviewer 的 FINISH 提案被拒绝")
  void shouldRejectFinishProposal() {
    String output = """
        {"type":"FINISH","content":"面试已覆盖核心考察点。","reason":"信息已充分"}
        """;

    assertThatThrownBy(() -> mapper.map(response(output), contextAtTurn(3)))
        .isInstanceOf(AdaptiveAgentResponseMapper.ModelOutputRejectionException.class)
        .hasMessageContaining("Unsupported");
  }

  @Test
  @DisplayName("流式文本直接解析为提问动作")
  void shouldMapStreamedTextToAsk() {
    String output = """
        {"type":"ASK","content":"Redis 缓存失效有哪些取舍？","reason":"继续验证工程权衡"}
        """;

    AgentAction action = mapper.mapText(output, context(null));

    assertThat(action).isEqualTo(RespondAction.ask(
        "Redis 缓存失效有哪些取舍？",
        "继续验证工程权衡"
    ));
  }

  @Test
  @DisplayName("流式文本缺必填字段时成为可纠正的模型输出拒绝")
  void shouldRejectStreamedTextWithMissingFields() {
    String output = """
        {"type":"ASK","content":"","reason":"继续"}
        """;

    assertThatThrownBy(() -> mapper.mapText(output, context(null)))
        .isInstanceOf(AdaptiveAgentResponseMapper.ModelOutputRejectionException.class)
        .hasMessage("Agent response is incomplete");
  }

}
