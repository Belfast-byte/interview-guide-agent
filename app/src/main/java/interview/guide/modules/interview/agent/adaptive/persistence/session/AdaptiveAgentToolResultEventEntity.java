package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
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
 * AdaptiveAgentToolResultEventEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(
    name = "agent_tool_result_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_tool_result_event",
        columnNames = {"tool_name", "result_id"}
    )
)
class AdaptiveAgentToolResultEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_index", nullable = false)
  private int turnIndex;

  @Column(name = "tool_name", nullable = false, length = 64)
  private String toolName;

  @Column(name = "result_id", nullable = false, length = 500)
  private String resultId;

  @Column(name = "result_summary", nullable = false, length = 500)
  private String resultSummary;

  @Column(name = "result_output", nullable = false, columnDefinition = "TEXT")
  private String resultOutput;

  @Column(name = "response_content", columnDefinition = "TEXT")
  private String responseContent;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ToolResultEventStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected AdaptiveAgentToolResultEventEntity() {}

  public AdaptiveAgentToolResultEventEntity(String sessionId, ToolResultEvent event) {
    this.sessionId = sessionId;
    turnIndex = event.turnIndex();
    toolName = event.toolName();
    resultId = event.resultId();
    resultSummary = event.summary();
    resultOutput = event.output();
    status = ToolResultEventStatus.RECEIVED;
  }

  public void complete() {
    status = ToolResultEventStatus.COMPLETED;
    completedAt = LocalDateTime.now();
  }

  public ToolResultFollowUp toFollowUp() {
    return new ToolResultFollowUp(
        resultId,
        turnIndex,
        responseContent,
        completedAt
    );
  }

  public long id() {
    return id;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
