package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveSessionStatus;
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

  AdaptiveAgentSessionEntity(
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

  AdaptiveInterviewSession toDomain() {
    return new AdaptiveInterviewSession(id, runtimeVersion, status, currentTurn, maxTurns);
  }

  String id() {
    return id;
  }

  String jd() {
    return jd;
  }

  String candidateId() {
    return candidateId;
  }

  String tenantId() {
    return tenantId;
  }

  String resume() {
    return resume;
  }

  String llmProvider() {
    return llmProvider;
  }

  AdaptiveSessionStatus status() {
    return status;
  }

  void apply(AdaptiveInterviewSession session) {
    status = session.status();
    currentTurn = session.currentTurn();
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
