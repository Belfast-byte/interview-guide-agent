package interview.guide.modules.interview.agent.adaptive.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmAssessmentMetricsSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxPolicyViolation;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlgorithmInterviewTelemetryTest {

  @Test
  @DisplayName("记录判题配额、队列、IE、结果有效性和端到端延迟")
  void shouldRecordAlgorithmLifecycleMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AlgorithmAssessmentMetricsSource metricsSource = mock(
        AlgorithmAssessmentMetricsSource.class
    );
    when(metricsSource.hasActiveJudging("session-1")).thenReturn(true);
    when(metricsSource.countAssessmentsWithValidResults()).thenReturn(4L);
    when(metricsSource.countAssessmentsWithSandboxEvidence()).thenReturn(4L);
    when(metricsSource.countReviewRequiredAssessments()).thenReturn(1L);
    AlgorithmInterviewTelemetry telemetry = new AlgorithmInterviewTelemetry(
        registry,
        metricsSource
    );
    LocalDateTime finishedAt = LocalDateTime.now();
    SandboxExecution execution = new SandboxExecution(
        "execution-1", "session-1", 10L, 1, SandboxWorkloadType.ALGORITHM,
        "two-sum", null, null, null,
        SandboxLanguage.JAVA, "source-ref", "a".repeat(64), SandboxRunMode.FULL,
        SandboxExecutionStatus.DONE, SandboxVerdict.WA, 4, 10, 120L, 32_768L, 7,
        null, finishedAt.minusSeconds(2), finishedAt, null
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
    telemetry.interviewTurnSubmitted("session-1");

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
    assertThat(registry.get(AlgorithmInterviewTelemetry.INTERVIEW_TURNS)
        .tag("judging", "active").counter().count()).isEqualTo(1);
    assertThat(registry.get(AlgorithmInterviewTelemetry.ASSESSMENTS).gauge().value())
        .isEqualTo(4);
    assertThat(registry.get(AlgorithmInterviewTelemetry.ASSESSMENTS_WITH_EVIDENCE)
        .gauge().value()).isEqualTo(4);
    assertThat(registry.get(AlgorithmInterviewTelemetry.ASSESSMENTS_REVIEW_REQUIRED)
        .gauge().value()).isEqualTo(1);
  }
}
