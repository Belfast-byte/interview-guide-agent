package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxPolicyViolation;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxVerdict;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlgorithmInterviewTelemetry {

  static final String SUBMISSIONS = "app.interview.adaptive.algorithm.submissions";
  static final String QUEUE_DEPTH = "app.interview.adaptive.algorithm.queue.depth";
  static final String ATTEMPTS = "app.interview.adaptive.algorithm.attempts";
  static final String RESULTS = "app.interview.adaptive.algorithm.results";
  static final String END_TO_END_DURATION =
      "app.interview.adaptive.algorithm.end-to-end.duration";
  static final String DEGRADATIONS = "app.interview.adaptive.algorithm.degradations";
  static final String POLICY_VIOLATIONS =
      "app.interview.adaptive.algorithm.policy.violations";

  private final MeterRegistry meterRegistry;
  private final AtomicLong queueDepth = new AtomicLong();

  public AlgorithmInterviewTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder(QUEUE_DEPTH, queueDepth, AtomicLong::get)
        .register(meterRegistry);
  }

  public void submissionAccepted() {
    submission("accepted");
  }

  public void quotaRejected() {
    submission("quota_rejected");
  }

  public void queueDepth(long depth) {
    queueDepth.set(depth);
  }

  public void attemptCompleted(SandboxVerdict verdict) {
    meterRegistry.counter(ATTEMPTS, "verdict", verdict.name()).increment();
  }

  public void attemptCompleted(
      String executionId,
      String sessionId,
      SandboxVerdict verdict,
      SandboxPolicyViolation policyViolation
  ) {
    attemptCompleted(verdict);
    if (policyViolation == null) {
      return;
    }
    meterRegistry.counter(
        POLICY_VIOLATIONS,
        "type", policyViolation.name()
    ).increment();
    log.warn(
        "algorithm_sandbox_policy_violation executionId={} sessionId={} type={}",
        executionId,
        sessionId,
        policyViolation
    );
  }

  public void resultReady(SandboxExecution execution) {
    String validity = execution.supersededBy() == null ? "valid" : "superseded";
    meterRegistry.counter(RESULTS, "validity", validity).increment();
    meterRegistry.timer(END_TO_END_DURATION, "status", execution.status().name())
        .record(Duration.between(execution.createdAt(), execution.finishedAt()));
  }

  public void degraded() {
    meterRegistry.counter(DEGRADATIONS).increment();
  }

  private void submission(String status) {
    meterRegistry.counter(SUBMISSIONS, "status", status).increment();
  }
}
