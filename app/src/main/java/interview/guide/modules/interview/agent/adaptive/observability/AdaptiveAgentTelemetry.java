package interview.guide.modules.interview.agent.adaptive.observability;

import io.micrometer.core.instrument.MeterRegistry;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.mcp.McpQuestionBankFailureReason;
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
  static final String TOOL_CALLS = "app.interview.adaptive.tool.calls";
  static final String TOOL_DURATION = "app.interview.adaptive.tool.duration";
  static final String MCP_QUESTION_BANK_FALLBACKS =
      "app.interview.adaptive.mcp.question-bank.fallbacks";

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

  public void planRejected(String sessionId, int errorCode) {
    meterRegistry.counter(
        DECISIONS,
        "role", "orchestrator",
        "status", "failure",
        "action", "PLAN"
    ).increment();
    log.warn(
        "adaptive_agent_failed phase=planning sessionId={} errorCode={}",
        sessionId,
        errorCode
    );
  }

  public void toolCallSucceeded(String role, String toolName, long startedNanos) {
    record(TOOL_CALLS, TOOL_DURATION, role, "success", toolName, startedNanos);
  }

  public void toolCallFailed(
      String role,
      String toolName,
      String sessionId,
      int turnIndex,
      int errorCode,
      long startedNanos
  ) {
    record(TOOL_CALLS, TOOL_DURATION, role, "failure", toolName, startedNanos);
    log.warn(
        "adaptive_agent_failed phase=tool role={} tool={} sessionId={} turnIndex={} "
            + "errorCode={} durationMs={}",
        role,
        toolName,
        sessionId,
        turnIndex,
        errorCode,
        elapsedMillis(startedNanos)
    );
  }

  public void mcpQuestionBankFallback(McpQuestionBankFailureReason reason) {
    meterRegistry.counter(
        MCP_QUESTION_BANK_FALLBACKS,
        "reason",
        reason.name()
    ).increment();
    log.warn("adaptive_agent_mcp_fallback source=question_bank reason={}", reason);
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
