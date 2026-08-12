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

  public void modelCallSucceeded(AgentResponseType action, long startedNanos) {
    record(MODEL_CALLS, MODEL_DURATION, "success", action.name(), startedNanos);
  }

  public void modelCallFailed(
      String sessionId,
      int inputTurn,
      int errorCode,
      long startedNanos
  ) {
    record(MODEL_CALLS, MODEL_DURATION, "failure", "none", startedNanos);
    log.warn(
        "adaptive_agent_failed phase=model sessionId={} inputTurn={} errorCode={} durationMs={}",
        sessionId,
        inputTurn,
        errorCode,
        elapsedMillis(startedNanos)
    );
  }

  public void decisionSucceeded(AgentResponseType action, long startedNanos) {
    record(DECISIONS, DECISION_DURATION, "success", action.name(), startedNanos);
  }

  public void decisionFailed(
      String sessionId,
      int inputTurn,
      int errorCode,
      long startedNanos
  ) {
    record(DECISIONS, DECISION_DURATION, "failure", "none", startedNanos);
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
      String status,
      String action,
      long startedNanos
  ) {
    meterRegistry.counter(counterName, "status", status, "action", action).increment();
    meterRegistry.timer(timerName, "status", status, "action", action)
        .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }
}
