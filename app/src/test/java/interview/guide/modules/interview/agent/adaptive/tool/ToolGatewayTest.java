package interview.guide.modules.interview.agent.adaptive.tool;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.role.AgentRoleRegistry;
import interview.guide.modules.interview.agent.adaptive.runtime.ReActRequest;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
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
    ToolGateway gateway = gateway(new StubTool("rubric_lookup", executions, "ok"), 100);

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
        new StubTool("rubric_lookup", new AtomicInteger(), "ok"),
        100
    );

    ToolExecution first = gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(Map.of("query", "Redis", "difficulty", "MEDIUM"))
    );
    ToolExecution second = gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(Map.of("query", "Redis", "difficulty", "MEDIUM"))
    );

    assertThat(first.invocationId()).isEqualTo(second.invocationId());
    assertThat(first.invocationId()).hasSize(64);
  }

  @Test
  @DisplayName("工具结果超过配置上限时截断并追加标注而非失败")
  void shouldTruncateOversizedResult() {
    ToolGateway gateway = gateway(
        new StubTool("rubric_lookup", new AtomicInteger(), "x".repeat(100)),
        20
    );

    ToolExecution execution = gateway.execute(
        request(AgentRole.INTERVIEWER),
        call(Map.of("query", "Redis"))
    );

    assertThat(execution.output()).hasSize(20 + "[truncated]".length());
    assertThat(execution.output()).endsWith("[truncated]");
  }

  @Test
  @DisplayName("Pending 工具结果保留句柄并进入异步结果类型")
  void shouldMapPendingToolResult() {
    ToolGateway gateway = gateway(new PendingStubTool(), 200);

    ToolExecution execution = gateway.execute(
        request(AgentRole.INTERVIEWER),
        new ToolCallAction("rubric_lookup", Map.of(), "异步提交")
    );

    assertThat(execution.resultId()).isEqualTo("submission-1");
    assertThat(execution.outcome()).isEqualTo(ToolExecutionOutcome.PENDING);
    assertThat(execution.turnIndex()).isEqualTo(4);
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
        new InterviewerContext(
            "JD",
            "Resume",
            0,
            6,
            0,
            "专业基础",
            "缓存",
            List.of("rubric_lookup"),
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            null,
            null
        )
    );
  }

  private ToolCallAction call(Map<String, Object> arguments) {
    return new ToolCallAction(
        "rubric_lookup",
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
      return new CompletedToolResult("result-1", output, "stub result");
    }
  }

  private record PendingStubTool() implements AdaptiveAgentTool {

    @Override
    public String name() {
      return "rubric_lookup";
    }

    @Override
    public ToolCallback callback() {
      return mock(ToolCallback.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
      return new PendingToolResult(
          "submission-1",
          Map.of("submissionId", "submission-1", "status", "PENDING"),
          "submission pending",
          4
      );
    }
  }
}
