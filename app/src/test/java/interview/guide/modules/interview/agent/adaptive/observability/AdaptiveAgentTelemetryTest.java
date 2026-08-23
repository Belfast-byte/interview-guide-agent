package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;

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
  @DisplayName("记录模型实际使用的输入、输出和总 Token")
  void shouldRecordActualModelTokenUsage() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.modelTokens(
        "planner",
        "session-1",
        new DefaultUsage(13, 5, 18)
    );

    assertThat(registry.summary(
        AdaptiveAgentTelemetry.MODEL_PROMPT_TOKENS,
        "role",
        "planner"
    ).totalAmount()).isEqualTo(13);
    assertThat(registry.summary(
        AdaptiveAgentTelemetry.MODEL_COMPLETION_TOKENS,
        "role",
        "planner"
    ).totalAmount()).isEqualTo(5);
    assertThat(registry.summary(
        AdaptiveAgentTelemetry.MODEL_TOTAL_TOKENS,
        "role",
        "planner"
    ).totalAmount()).isEqualTo(18);
  }

  @Test
  @DisplayName("按维度和深度记录已持久化评估及有效证据数量")
  void shouldRecordAssessmentMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.assessmentRecorded("专业基础", DepthLevel.L3, 2);

    assertThat(registry.counter(
        AdaptiveAgentTelemetry.ASSESSMENTS,
        "dimension", "专业基础",
        "depth", "L3"
    ).count()).isEqualTo(1);
    assertThat(registry.summary(
        AdaptiveAgentTelemetry.ASSESSMENT_EVIDENCES,
        "dimension", "专业基础",
        "depth", "L3"
    ).totalAmount()).isEqualTo(2);
  }

  @Test
  @DisplayName("按深度是否提升记录追问收益")
  void shouldRecordFollowUpYield() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AdaptiveAgentTelemetry telemetry = new AdaptiveAgentTelemetry(registry);

    telemetry.followUpAssessed("专业基础", DepthLevel.L1, DepthLevel.L3);
    telemetry.followUpAssessed("专业基础", DepthLevel.L3, DepthLevel.L3);

    assertThat(registry.counter(
        AdaptiveAgentTelemetry.ASSESSMENT_FOLLOW_UPS,
        "dimension", "专业基础",
        "outcome", "improved"
    ).count()).isEqualTo(1);
    assertThat(registry.counter(
        AdaptiveAgentTelemetry.ASSESSMENT_FOLLOW_UPS,
        "dimension", "专业基础",
        "outcome", "not_improved"
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
