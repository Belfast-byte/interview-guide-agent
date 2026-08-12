package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "agent_tool_calls",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_tool_call_invocation",
        columnNames = "invocation_id"
    )
)
public class AdaptiveAgentToolCallEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "invocation_id", nullable = false, length = 64)
  private String invocationId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_index", nullable = false)
  private int turnIndex;

  @Column(nullable = false, length = 32)
  private String role;

  @Column(name = "tool_name", nullable = false, length = 64)
  private String toolName;

  @Column(name = "decision_reason", nullable = false, length = 500)
  private String reason;

  @Column(name = "input_summary", nullable = false, length = 500)
  private String inputSummary;

  @Column(name = "output_summary", nullable = false, length = 500)
  private String outputSummary;

  @Column(name = "result_id", nullable = false, length = 500)
  private String resultId;

  @Column(name = "duration_ms", nullable = false)
  private long durationMillis;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentToolCallEntity() {}

  AdaptiveAgentToolCallEntity(String sessionId, ToolExecution execution) {
    this.invocationId = execution.invocationId();
    this.sessionId = sessionId;
    this.turnIndex = execution.turnIndex();
    this.role = execution.role();
    this.toolName = execution.toolName();
    this.reason = execution.reason();
    this.inputSummary = execution.inputSummary();
    this.outputSummary = execution.outputSummary();
    this.resultId = execution.resultId();
    this.durationMillis = execution.durationMillis();
  }

  String invocationId() {
    return invocationId;
  }

  String sessionId() {
    return sessionId;
  }

  int turnIndex() {
    return turnIndex;
  }

  String role() {
    return role;
  }

  String toolName() {
    return toolName;
  }

  String reason() {
    return reason;
  }

  String inputSummary() {
    return inputSummary;
  }

  String outputSummary() {
    return outputSummary;
  }

  String resultId() {
    return resultId;
  }

  long durationMillis() {
    return durationMillis;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
