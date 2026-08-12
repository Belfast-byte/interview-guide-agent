package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveAgentTelemetryTest {

  @Test
  @DisplayName("模型与裁决指标记录状态、动作和耗时")
  void shouldRecordModelAndDecisionMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.modelCallSucceeded("interviewer", AgentResponseType.ASK.name(), System.nanoTime());
    telemetry.decisionSucceeded(AgentResponseType.ASK, System.nanoTime());

    assertThat(registry.counter(
        AdaptiveAgentTelemetry.MODEL_CALLS,
        "role",
        "interviewer",
        "status",
        "success",
        "action",
        "ASK"
    ).count()).isEqualTo(1);
    assertThat(registry.timer(
        AdaptiveAgentTelemetry.MODEL_DURATION,
        "role",
        "interviewer",
        "status",
        "success",
        "action",
        "ASK"
    ).count()).isEqualTo(1);
    assertThat(registry.counter(
        AdaptiveAgentTelemetry.DECISIONS,
        "role",
        "orchestrator",
        "status",
        "success",
        "action",
        "ASK"
    ).count()).isEqualTo(1);
  }

  @Test
  @DisplayName("状态冲突有独立计数器")
  void shouldRecordStateConflict() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.stateConflict("session-1", 1);

    assertThat(registry.counter(AdaptiveAgentTelemetry.STATE_CONFLICTS).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("工具指标只记录角色、工具、状态和耗时")
  void shouldRecordToolMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.toolCallSucceeded(
        "INTERVIEWER",
        "question_bank_search",
        System.nanoTime()
    );

    assertThat(registry.counter(
        AdaptiveAgentTelemetry.TOOL_CALLS,
        "role", "INTERVIEWER",
        "status", "success",
        "action", "question_bank_search"
    ).count()).isEqualTo(1);
  }
}
