package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ToolGatewayTest {

  @Test
  @DisplayName("角色白名单在工具执行前拒绝越权调用")
  void shouldRejectUnauthorizedRoleBeforeExecution() {
    AtomicInteger executions = new AtomicInteger();
    ToolGateway gateway = gateway(new StubTool("question_bank_search", executions, "ok"), 100);

    assertThatThrownBy(() -> gateway.execute(
        request(AgentRole.PLANNER),
        call(Map.of("query", "Redis"))
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("not allowed");
    assertThat(executions).hasValue(0);
  }

  @Test
  @DisplayName("相同会话轮次工具和参数生成相同幂等键")
  void shouldCreateStableInvocationId() {
    ToolGateway gateway = gateway(
        new StubTool("question_bank_search", new AtomicInteger(), "ok"),
        100
    );
    Map<String, Object> firstArguments = new LinkedHashMap<>();
    firstArguments.put("query", "Redis");
    firstArguments.put("difficulty", "MEDIUM");
    Map<String, Object> reorderedArguments = new LinkedHashMap<>();
    reorderedArguments.put("difficulty", "MEDIUM");
    reorderedArguments.put("query", "Redis");

    ToolExecution first = gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(firstArguments)
    );
    ToolExecution second = gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(reorderedArguments)
    );

    assertThat(first.invocationId()).isEqualTo(second.invocationId());
    assertThat(first.invocationId()).hasSize(64);
  }

  @Test
  @DisplayName("工具结果超过配置上限时失败而不静默截断")
  void shouldRejectOversizedResult() {
    ToolGateway gateway = gateway(
        new StubTool("question_bank_search", new AtomicInteger(), "x".repeat(100)),
        20
    );

    assertThatThrownBy(() -> gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(Map.of("query", "Redis"))
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("too large");
  }

  private ToolGateway gateway(AdaptiveAgentTool tool, int maxResultChars) {
    AdaptiveAgentProperties agentProperties = new AdaptiveAgentProperties();
    ToolProperties toolProperties = new ToolProperties();
    toolProperties.setMaxResultChars(maxResultChars);
    return new ToolGateway(
        List.of(tool),
        new AgentRoleRegistry(agentProperties),
        new ObjectMapper(),
        new AdaptiveAgentTelemetry(new SimpleMeterRegistry()),
        toolProperties
    );
  }

  private ReActRequest request(AgentRole role) {
    return new ReActRequest(
        "session-1",
        role,
        null,
        "JD",
        "Resume",
        6,
        "专业基础",
        "缓存",
        List.of("question_bank_search"),
        null,
        List.of(),
        null
    );
  }

  private ToolCallAction call(Map<String, Object> arguments) {
    return new ToolCallAction(
        "question_bank_search",
        arguments,
        "读取审核题"
    );
  }

  private record StubTool(
      String name,
      AtomicInteger executions,
      String output
  ) implements AdaptiveAgentTool {

    @Override
    public ToolCallback callback() {
      return mock(ToolCallback.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
      executions.incrementAndGet();
      return new ToolResult("result-1", output, "stub result");
    }
  }
}
