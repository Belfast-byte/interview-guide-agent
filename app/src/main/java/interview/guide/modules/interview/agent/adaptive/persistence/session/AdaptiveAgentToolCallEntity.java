package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecution;
import interview.guide.modules.interview.agent.adaptive.runtime.ToolExecutionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * AdaptiveAgentToolCallEntity JPA 实体，对应数据库中的相关表。
 */
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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ToolExecutionOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveAgentToolCallEntity() {}

  public AdaptiveAgentToolCallEntity(String sessionId, ToolExecution execution) {
    this.invocationId = execution.invocationId();
    this.sessionId = sessionId;
    this.turnIndex = execution.turnIndex();
    this.role = execution.role();
    this.toolName = execution.toolName();
    this.reason = execution.reason();
    this.inputSummary = execution.inputSummary();
    this.outputSummary = execution.outputSummary();
    this.resultId = execution.resultId();
    this.outcome = execution.outcome();
    this.durationMillis = execution.durationMillis();
  }

  public String invocationId() {
    return invocationId;
  }

  public Long id() {
    return id;
  }

  public String sessionId() {
    return sessionId;
  }

  public int turnIndex() {
    return turnIndex;
  }

  public String role() {
    return role;
  }

  public String toolName() {
    return toolName;
  }

  public String reason() {
    return reason;
  }

  public String inputSummary() {
    return inputSummary;
  }

  public String outputSummary() {
    return outputSummary;
  }

  public String resultId() {
    return resultId;
  }

  public long durationMillis() {
    return durationMillis;
  }

  public ToolExecutionOutcome outcome() {
    return outcome;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
