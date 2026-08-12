package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.MeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdaptiveAgentTelemetry {

  static final String MODEL_CALLS = "app.interview.adaptive.model.calls";
  static final String MODEL_DURATION = "app.interview.adaptive.model.duration";
  static final String DECISIONS = "app.interview.adaptive.decisions";
  static final String DECISION_DURATION = "app.interview.adaptive.decision.duration";
  static final String STATE_CONFLICTS = "app.interview.adaptive.state.conflicts";

  private final MeterRegistry meterRegistry;

  public void modelCallSucceeded(
      String role,
      String action,
      long startedNanos
  ) {
    record(MODEL_CALLS, MODEL_DURATION, role, "success", action, startedNanos);
  }

  public void modelCallFailed(
      String role,
      String sessionId,
      int inputTurn,
      int errorCode,
      long startedNanos
  ) {
    record(MODEL_CALLS, MODEL_DURATION, role, "failure", "none", startedNanos);
    log.warn(
        "adaptive_agent_failed phase=model role={} sessionId={} inputTurn={} "
            + "errorCode={} durationMs={}",
        role,
        sessionId,
        inputTurn,
        errorCode,
        elapsedMillis(startedNanos)
    );
  }

  public void decisionSucceeded(AgentResponseType action, long startedNanos) {
    record(
        DECISIONS,
        DECISION_DURATION,
        "orchestrator",
        "success",
        action.name(),
        startedNanos
    );
  }

  public void decisionFailed(
      String sessionId,
      int inputTurn,
      int errorCode,
      long startedNanos
  ) {
    record(
        DECISIONS,
        DECISION_DURATION,
        "orchestrator",
        "failure",
        "none",
        startedNanos
    );
    log.warn(
        "adaptive_agent_failed phase=runtime sessionId={} inputTurn={} errorCode={} durationMs={}",
        sessionId,
        inputTurn,
        errorCode,
        elapsedMillis(startedNanos)
    );
  }

  public void stateConflict(String sessionId, int inputTurn) {
    meterRegistry.counter(STATE_CONFLICTS).increment();
    log.warn(
        "adaptive_agent_failed phase=persistence sessionId={} inputTurn={} reason=state_conflict",
        sessionId,
        inputTurn
    );
  }

  private void record(
      String counterName,
      String timerName,
      String role,
      String status,
      String action,
      long startedNanos
  ) {
    meterRegistry.counter(
        counterName,
        "role",
        role,
        "status",
        status,
        "action",
        action
    ).increment();
    meterRegistry.timer(
        timerName,
        "role",
        role,
        "status",
        status,
        "action",
        action
    )
        .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }
}
