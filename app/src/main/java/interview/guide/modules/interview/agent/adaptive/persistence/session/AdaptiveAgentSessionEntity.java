package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * AdaptiveAgentSessionEntity JPA 实体，对应数据库中的相关表。
 */
@Entity
@Table(name = "agent_sessions")
public class AdaptiveAgentSessionEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "runtime_version", nullable = false, length = 32)
  private String runtimeVersion;

  @Column(name = "candidate_id", nullable = false, length = 64)
  private String candidateId;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String jd;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String resume;

  @Column(name = "llm_provider", length = 64)
  private String llmProvider;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdaptiveSessionStatus status;

  @Column(name = "current_turn", nullable = false)
  private int currentTurn;

  @Column(name = "max_turns", nullable = false)
  private int maxTurns;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  protected AdaptiveAgentSessionEntity() {}

  public AdaptiveAgentSessionEntity(
      AdaptiveInterviewSession session,
      String tenantId,
      String candidateId,
      String jd,
      String resume,
      String llmProvider
  ) {
    this.id = session.id();
    this.runtimeVersion = session.runtimeVersion();
    this.tenantId = tenantId;
    this.candidateId = candidateId;
    this.jd = jd;
    this.resume = resume;
    this.llmProvider = llmProvider;
    this.status = session.status();
    this.currentTurn = session.currentTurn();
    this.maxTurns = session.maxTurns();
  }

  public AdaptiveInterviewSession toDomain() {
    return new AdaptiveInterviewSession(id, runtimeVersion, status, currentTurn, maxTurns);
  }

  public String id() {
    return id;
  }

  public String jd() {
    return jd;
  }

  public String candidateId() {
    return candidateId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String resume() {
    return resume;
  }

  public String llmProvider() {
    return llmProvider;
  }

  public AdaptiveSessionStatus status() {
    return status;
  }

  public LocalDateTime completedAt() {
    return completedAt;
  }

  public String failureReason() {
    return failureReason;
  }

  public AdaptiveSessionStatus markFailed(String reason) {
    if (status != AdaptiveSessionStatus.CREATED) {
      return status;
    }
    status = AdaptiveSessionStatus.FAILED;
    failureReason = reason != null && reason.length() > 500
        ? reason.substring(0, 500)
        : reason;
    return status;
  }

  public void apply(AdaptiveInterviewSession session) {
    status = session.status();
    currentTurn = session.currentTurn();
    maxTurns = session.maxTurns();
    if (status == AdaptiveSessionStatus.COMPLETED) {
      completedAt = LocalDateTime.now();
    }
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
