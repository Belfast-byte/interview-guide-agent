package interview.guide.modules.interview.agent.adaptive.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.AdoptableSource;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation.Kind;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolBatch;
import interview.guide.modules.interview.agent.adaptive.runtime.ReadToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolGatewayTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectionScenarios")
  @DisplayName("白名单、schema 与 scope 校验在 dispatch 前返回拒绝 Observation")
  void shouldRejectBeforeDispatch(
      String name,
      List<String> allowlist,
      Map<String, Object> arguments,
      String expectedField
  ) {
    AtomicInteger executions = new AtomicInteger();
    ToolGateway gateway = new ToolGateway(List.of(new ValidatingTool(executions)));

    List<DecisionObservation> observations = gateway.execute(batch(
        context(allowlist), List.of(call("rubric_search", arguments)), 0));

    assertThat(observations).singleElement().satisfies(observation -> {
      assertThat(observation.reference()).isEqualTo("tool-0-0");
      assertThat(observation.kind()).isEqualTo(Kind.VALIDATION_REJECTION);
      assertThat(observation.field()).isEqualTo(expectedField);
      assertThat(observation.adoptableSources()).isEmpty();
    });
    assertThat(executions).hasValue(0);
  }

  @Test
  @DisplayName("调用按模型顺序串行执行且同步终态形成可区分 Observation")
  void shouldPreserveOrderAndMapResults() {
    List<String> order = new ArrayList<>();
    ToolGateway gateway = new ToolGateway(List.of(new ResultTool(order)));
    List<ReadToolCall> calls = List.of(
        call("result_tool", Map.of("mode", "success")),
        call("result_tool", Map.of("mode", "empty")),
        call("result_tool", Map.of("mode", "timeout")),
        call("result_tool", Map.of("mode", "error"))
    );

    List<DecisionObservation> observations = gateway.execute(
        batch(context(List.of("result_tool")), calls, 3));

    assertThat(order).containsExactly("success", "empty", "timeout", "error");
    assertThat(observations).extracting(DecisionObservation::reference)
        .containsExactly("tool-3-0", "tool-3-1", "tool-3-2", "tool-3-3");
    assertThat(observations).extracting(DecisionObservation::kind)
        .containsExactly(
            Kind.TOOL_SUCCESS, Kind.TOOL_EMPTY, Kind.TOOL_TIMEOUT, Kind.TOOL_ERROR);
    assertThat(observations.getFirst().data()).containsEntry("rubric", "按事实评分");
    assertThat(observations.getFirst().adoptableSources()).containsExactly(
        new AdoptableSource("rubric:question:1@v1", "rubric", "question:1", "v1"));
    assertThat(observations.subList(1, observations.size()))
        .allMatch(observation -> observation.adoptableSources().isEmpty());
  }

  @Test
  @DisplayName("共享绝对 deadline 耗尽后终止批次且不 dispatch 后续调用")
  void shouldStopWhenSharedDeadlineIsExhausted() {
    AtomicInteger executions = new AtomicInteger();
    ToolGateway gateway = new ToolGateway(List.of(new ValidatingTool(executions)));
    ReadToolBatch batch = new ReadToolBatch(
        context(List.of("rubric_search")),
        List.of(
            call("rubric_search", Map.of("query", "first")),
            call("rubric_search", Map.of("query", "second"))
        ),
        System.nanoTime(),
        0
    );

    assertThatThrownBy(() -> gateway.execute(batch))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("资源截止时间已耗尽");
    assertThat(executions).hasValue(0);
  }

  private static Stream<Arguments> rejectionScenarios() {
    return Stream.of(
        Arguments.of("非白名单", List.of(), Map.of("query", "Redis"), "toolName"),
        Arguments.of(
            "schema 非法", List.of("rubric_search"), Map.of(), "arguments.query"),
        Arguments.of(
            "scope 越界",
            List.of("rubric_search"),
            Map.of("query", "Redis", "tenantId", "other"),
            "arguments.tenantId")
    );
  }

  private ReadToolBatch batch(
      AgentContext context,
      List<ReadToolCall> calls,
      int batchIndex
  ) {
    return new ReadToolBatch(context, calls, System.nanoTime() + 1_000_000_000L, batchIndex);
  }

  private ReadToolCall call(String toolName, Map<String, Object> arguments) {
    return new ReadToolCall(toolName, arguments, "读取评分事实");
  }

  private AgentContext context(List<String> allowedReadTools) {
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                "session-1", "provider-1", new MemoryOwner("tenant-1", "candidate-1")),
            SessionMode.EVALUATION,
            4
        ),
        new AgentContext.Facts(
            new CoverageView(0, 4, List.of(), List.of(), List.of()),
            List.of(),
            List.of(),
            allowedReadTools
        ),
        WorkingMemory.empty()
    );
  }

  private record ValidatingTool(AtomicInteger executions) implements ReadOnlyAgentTool {

    @Override
    public String name() {
      return "rubric_search";
    }

    @Override
    public void validate(ReadToolRequest request) {
      Object query = request.arguments().get("query");
      if (!(query instanceof String text) || text.isBlank()) {
        throw new ReadToolValidationException("arguments.query", "query 不能为空");
      }
      if (request.arguments().containsKey("tenantId")) {
        throw new ReadToolValidationException(
            "arguments.tenantId", "scope 只能来自服务端 AgentContext");
      }
    }

    @Override
    public ReadToolResult execute(ReadToolRequest request) {
      executions.incrementAndGet();
      return new ReadToolResult.Empty("没有命中");
    }
  }

  private record ResultTool(List<String> order) implements ReadOnlyAgentTool {

    @Override
    public String name() {
      return "result_tool";
    }

    @Override
    public void validate(ReadToolRequest request) {}

    @Override
    public ReadToolResult execute(ReadToolRequest request) {
      String mode = (String) request.arguments().get("mode");
      order.add(mode);
      return switch (mode) {
        case "success" -> new ReadToolResult.Success(
            Map.of("rubric", "按事实评分"),
            List.of(new AdoptableSource(
                "rubric:question:1@v1", "rubric", "question:1", "v1"))
        );
        case "empty" -> new ReadToolResult.Empty("没有命中");
        case "timeout" -> new ReadToolResult.Timeout("查询超时");
        case "error" -> new ReadToolResult.Error("查询失败");
        default -> throw new IllegalArgumentException("未知测试模式");
      };
    }
  }
}
