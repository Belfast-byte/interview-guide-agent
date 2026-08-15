package interview.guide.modules.interview.agent.adaptive.observability;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxPolicyViolation;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxVerdict;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlgorithmInterviewTelemetryTest {

  @Test
  @DisplayName("记录判题配额、队列、IE、结果有效性和端到端延迟")
  void shouldRecordAlgorithmLifecycleMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AlgorithmInterviewTelemetry telemetry = new AlgorithmInterviewTelemetry(registry);
    LocalDateTime finishedAt = LocalDateTime.now();
    SandboxExecution execution = new SandboxExecution(
        "execution-1", "session-1", 10L, 1, "two-sum",
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.DONE, SandboxVerdict.WA, 4, 10, 120L, 32_768L, 7,
        null, false, 0, finishedAt.minusSeconds(2), finishedAt
    );

    telemetry.submissionAccepted();
    telemetry.quotaRejected();
    telemetry.queueDepth(3);
    telemetry.attemptCompleted(SandboxVerdict.IE);
    telemetry.attemptCompleted(
        "execution-1",
        "session-1",
        SandboxVerdict.RE,
        SandboxPolicyViolation.NETWORK_ACCESS
    );
    telemetry.resultReady(execution);
    telemetry.degraded();

    assertThat(registry.get(AlgorithmInterviewTelemetry.SUBMISSIONS)
        .tag("status", "accepted").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.SUBMISSIONS)
        .tag("status", "quota_rejected").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.QUEUE_DEPTH).gauge().value())
        .isEqualTo(3);
    assertThat(registry.get(AlgorithmInterviewTelemetry.ATTEMPTS)
        .tag("verdict", "IE").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.RESULTS)
        .tag("validity", "valid").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.END_TO_END_DURATION)
        .tag("status", "DONE").timer().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.POLICY_VIOLATIONS)
        .tag("type", "NETWORK_ACCESS").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.DEGRADATIONS).counter().count())
        .isEqualTo(1);
  }
}
