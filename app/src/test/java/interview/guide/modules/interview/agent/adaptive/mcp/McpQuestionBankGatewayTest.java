package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.ToolProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpQuestionBankGatewayTest {

  @Mock
  private McpSyncClient client;

  private McpQuestionBankProperties properties;
  private McpQuestionBankGateway gateway;

  @BeforeEach
  void setUp() {
    properties = new McpQuestionBankProperties();
    ToolProperties toolProperties = new ToolProperties();
    gateway = new McpQuestionBankGateway(
        List.of(client),
        new ObjectMapper(),
        new DeadlineExecutor(),
        properties,
        toolProperties
    );
    when(client.getServerInfo()).thenReturn(
        Implementation.builder("question-bank", "1.0.0").build()
    );
  }

  @Test
  @DisplayName("远端 MCP 题库使用稳定契约返回审核题")
  void shouldReturnGovernedRemoteQuestions() {
    when(client.callTool(any())).thenReturn(result("""
        [{
          "stableId": "question:42",
          "id": 42,
          "category": "Redis",
          "difficulty": "mid",
          "question": "Redis 缓存一致性如何保证？"
        }]
        """));

    List<QuestionBankQuestion> questions = gateway.search("缓存一致性", "mid");

    assertThat(questions).containsExactly(new QuestionBankQuestion(
        "question:42",
        42L,
        "Redis",
        "mid",
        "Redis 缓存一致性如何保证？"
    ));
    ArgumentCaptor<CallToolRequest> request = ArgumentCaptor.forClass(
        CallToolRequest.class
    );
    verify(client).callTool(request.capture());
    assertThat(request.getValue().name()).isEqualTo("question_bank_search");
    assertThat(request.getValue().arguments()).containsEntry("query", "缓存一致性")
        .containsEntry("difficulty", "mid");
  }

  @Test
  @DisplayName("远端 MCP 返回伪造稳定 ID 时拒绝结果")
  void shouldRejectMalformedStableId() {
    when(client.callTool(any())).thenReturn(result("""
        [{
          "stableId": "question:other",
          "id": 42,
          "question": "问题？"
        }]
        """));

    assertThatThrownBy(() -> gateway.search("缓存", null))
        .isInstanceOfSatisfying(McpQuestionBankException.class, exception ->
            assertThat(exception.reason())
                .isEqualTo(McpQuestionBankFailureReason.MALFORMED_RESPONSE)
        );
  }

  @Test
  @DisplayName("远端 MCP 黑洞在独立 deadline 内失败")
  void shouldStopAtRemoteDeadline() {
    properties.setDeadline(Duration.ofMillis(30));
    when(client.callTool(any())).thenAnswer(invocation -> {
      Thread.sleep(5_000);
      return result("[]");
    });

    assertThatThrownBy(() -> gateway.search("缓存", null))
        .isInstanceOfSatisfying(McpQuestionBankException.class, exception ->
            assertThat(exception.reason())
                .isEqualTo(McpQuestionBankFailureReason.TIMEOUT)
        );
  }

  @Test
  @DisplayName("远端 MCP 返回 null 文本按格式不合法处理而非抛出 NPE")
  void shouldRejectNullTextContent() {
    TextContent content = mock(TextContent.class);
    when(content.text()).thenReturn(null);
    when(client.callTool(any())).thenReturn(
        CallToolResult.builder().addContent(content).build()
    );

    assertThatThrownBy(() -> gateway.search("缓存", null))
        .isInstanceOfSatisfying(McpQuestionBankException.class, exception ->
            assertThat(exception.reason())
                .isEqualTo(McpQuestionBankFailureReason.MALFORMED_RESPONSE)
        );
  }

  @Test
  @DisplayName("MCP Server 信息读取异常按未找到 Server 处理以触发本地回退")
  void shouldFallbackWhenServerInfoReadFails() {
    when(client.getServerInfo()).thenThrow(new RuntimeException("连接中断"));

    assertThatThrownBy(() -> gateway.search("缓存", null))
        .isInstanceOfSatisfying(McpQuestionBankException.class, exception ->
            assertThat(exception.reason())
                .isEqualTo(McpQuestionBankFailureReason.SERVER_NOT_FOUND)
        );
  }

  private CallToolResult result(String json) {
    return CallToolResult.builder().addTextContent(json).build();
  }
}
