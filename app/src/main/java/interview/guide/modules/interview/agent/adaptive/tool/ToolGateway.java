package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleDefinition;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentToolExecutor;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具网关，负责执行 Agent 请求的工具调用并管理同步/异步结果。
 */
@Component
public class ToolGateway implements AgentToolExecutor {

  private final Map<String, AdaptiveAgentTool> tools;
  private final AgentRoleRegistry roleRegistry;
  private final ObjectMapper objectMapper;
  private final AdaptiveAgentTelemetry telemetry;
  private final int maxResultChars;

  public ToolGateway(
      List<AdaptiveAgentTool> tools,
      AgentRoleRegistry roleRegistry,
      ObjectMapper objectMapper,
      AdaptiveAgentTelemetry telemetry,
      ToolProperties properties
  ) {
    this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(
        AdaptiveAgentTool::name,
        Function.identity()
    ));
    this.roleRegistry = roleRegistry;
    this.objectMapper = objectMapper;
    this.telemetry = telemetry;
    this.maxResultChars = properties.getMaxResultChars();
  }

  /**
   * 执行一次 Agent 工具调用：校验角色白名单、调用具体工具、记录执行轨迹和遥测。
   *
   * @param request ReAct 请求上下文
   * @param action 工具调用动作
   * @return 工具执行结果
   */
  @Override
  public ToolExecution execute(ReActRequest request, ToolCallAction action) {
    long startedNanos = System.nanoTime();
    try {
      ToolExecution execution = executeAllowed(request, action, startedNanos);
      telemetry.toolCallSucceeded(request.role().name(), action.toolName(), startedNanos);
      return execution;
    } catch (BusinessException e) {
      telemetry.toolCallFailed(
          request.role().name(),
          action.toolName(),
          request.sessionId(),
          request.targetTurnIndex(),
          e.getCode(),
          startedNanos
      );
      throw e;
    } catch (Exception e) {
      telemetry.toolCallFailed(
          request.role().name(),
          action.toolName(),
          request.sessionId(),
          request.targetTurnIndex(),
          ErrorCode.AI_SERVICE_ERROR.getCode(),
          startedNanos
      );
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool execution failed",
          e
      );
    }
  }

  private ToolExecution executeAllowed(
      ReActRequest request,
      ToolCallAction action,
      long startedNanos
  ) {
    AgentRoleDefinition role = roleRegistry.get(request.role());
    if (!role.allowedTools().contains(action.toolName())) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent role is not allowed to call tool: " + action.toolName()
      );
    }
    AdaptiveAgentTool tool = tools.get(action.toolName());
    if (tool == null) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent requested an unavailable tool: " + action.toolName()
      );
    }

    String argumentsJson = writeJson(canonicalize(action.arguments()));
    String invocationId = sha256(String.join(
        "\n",
        request.sessionId(),
        Integer.toString(request.targetTurnIndex()),
        action.toolName(),
        argumentsJson
    ));
    ToolResult result = tool.execute(request, action.arguments());
    String output = writeJson(result.value());
    if (output.length() > maxResultChars) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent tool result is too large");
    }
    return new ToolExecution(
        invocationId,
        action.toolName(),
        action.reason(),
        request.role().name(),
        result instanceof PendingToolResult pending && pending.targetTurnIndex() != null
            ? pending.targetTurnIndex()
            : request.targetTurnIndex(),
        "keys=" + action.arguments().keySet().stream().sorted().toList(),
        result.summary(),
        result.resultId(),
        output,
        result instanceof PendingToolResult
            ? ToolExecutionOutcome.PENDING
            : ToolExecutionOutcome.COMPLETED,
        (System.nanoTime() - startedNanos) / 1_000_000
    );
  }

  /**
   * 按角色白名单生成 Spring AI ToolCallback 列表，供模型调用。
   *
   * @param role 角色定义
   * @return 该角色可用的工具回调
   */
  public List<ToolCallback> callbacksFor(AgentRoleDefinition role) {
    return role.allowedTools().stream()
        .sorted()
        .map(tools::get)
        .map(AdaptiveAgentTool::callback)
        .toList();
  }

  private Object canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = map.entrySet().stream()
          .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
          .collect(Collectors.toMap(
              entry -> String.valueOf(entry.getKey()),
              entry -> canonicalize(entry.getValue()),
              (left, right) -> left,
              LinkedHashMap::new
          ));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::canonicalize).toList();
    }
    return value;
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new BusinessException(
          ErrorCode.AI_SERVICE_ERROR,
          "Agent tool data serialization failed",
          e
      );
    }
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 is unavailable", e);
    }
  }
}
