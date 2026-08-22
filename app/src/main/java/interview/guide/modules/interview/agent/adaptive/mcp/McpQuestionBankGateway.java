package interview.guide.modules.interview.agent.adaptive.mcp;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import interview.guide.modules.interview.agent.adaptive.tool.QuestionBankQuestion;
import interview.guide.modules.interview.agent.adaptive.tool.ToolProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP 题库网关，通过远程 MCP 服务检索题库。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent.mcp.question-bank",
    name = "enabled",
    havingValue = "true"
)
public class McpQuestionBankGateway {

  private final List<McpSyncClient> clients;
  private final ObjectMapper objectMapper;
  private final DeadlineExecutor deadlineExecutor;
  private final McpQuestionBankProperties properties;
  private final ToolProperties toolProperties;

  public List<QuestionBankQuestion> search(String query, String difficulty) {
    McpSyncClient client = findClient();
    Map<String, Object> arguments = difficulty == null
        ? Map.of("query", query)
        : Map.of("query", query, "difficulty", difficulty);
    CallToolResult result;
    try {
      result = deadlineExecutor.invoke(
          () -> client.callTool(CallToolRequest.builder(properties.getToolName())
              .arguments(arguments)
              .build()),
          System.nanoTime() + properties.getDeadline().toNanos(),
          "远端题库 MCP 调用"
      );
    } catch (BusinessException e) {
      McpQuestionBankFailureReason reason = e.getCode() == ErrorCode.AI_SERVICE_TIMEOUT.getCode()
          ? McpQuestionBankFailureReason.TIMEOUT
          : McpQuestionBankFailureReason.REMOTE_ERROR;
      throw new McpQuestionBankException(reason, "远端题库 MCP 调用失败", e);
    }
    return parse(result);
  }

  private McpSyncClient findClient() {
    McpSyncClient client;
    try {
      client = clients.stream()
          .filter(candidate -> properties.getServerName().equals(
              candidate.getServerInfo().name()
          ))
          .findFirst()
          .orElse(null);
    } catch (RuntimeException e) {
      throw new McpQuestionBankException(
          McpQuestionBankFailureReason.SERVER_NOT_FOUND,
          "远端题库 MCP Server 信息读取失败",
          e
      );
    }
    if (client == null) {
      throw new McpQuestionBankException(
          McpQuestionBankFailureReason.SERVER_NOT_FOUND,
          "未找到配置的远端题库 MCP Server"
      );
    }
    return client;
  }

  private List<QuestionBankQuestion> parse(CallToolResult result) {
    if (Boolean.TRUE.equals(result.isError())
        || result.content().size() != 1
        || !(result.content().getFirst() instanceof TextContent textContent)
        || textContent.text() == null
        || textContent.text().length() > properties.getMaxResponseChars()) {
      throw new McpQuestionBankException(
          McpQuestionBankFailureReason.MALFORMED_RESPONSE,
          "远端题库 MCP 返回格式不合法"
      );
    }
    try {
      List<QuestionBankQuestion> questions = objectMapper.readValue(
          textContent.text(),
          new TypeReference<>() {}
      );
      validate(questions);
      return List.copyOf(questions);
    } catch (JacksonException e) {
      throw new McpQuestionBankException(
          McpQuestionBankFailureReason.MALFORMED_RESPONSE,
          "远端题库 MCP 返回无法解析",
          e
      );
    }
  }

  private void validate(List<QuestionBankQuestion> questions) {
    if (questions == null || questions.size() > toolProperties.getQuestionBankLimit()) {
      throw new McpQuestionBankException(
          McpQuestionBankFailureReason.MALFORMED_RESPONSE,
          "远端题库 MCP 返回数量不合法"
      );
    }
    for (QuestionBankQuestion question : questions) {
      if (question == null
          || question.id() == null
          || !(("question:" + question.id()).equals(question.stableId()))
          || question.question() == null
          || question.question().isBlank()) {
        throw new McpQuestionBankException(
            McpQuestionBankFailureReason.MALFORMED_RESPONSE,
            "远端题库 MCP 返回题目不合法"
        );
      }
    }
  }
}
