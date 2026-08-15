package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
class AnalysisJobEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "repository_id", nullable = false, length = 36)
  private String repositoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AnalysisJobStatus status;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "token_cost")
  private Long tokenCost;

  @Column(name = "failure_reason", length = 200)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  protected AnalysisJobEntity() {}

  AnalysisJobEntity(String id, String sessionId, String repositoryId) {
    this.id = id;
    this.sessionId = sessionId;
    this.repositoryId = repositoryId;
    this.status = AnalysisJobStatus.PENDING;
  }

  CodeAnalysisJob toDomain() {
    return new CodeAnalysisJob(
        id,
        sessionId,
        repositoryId,
        status,
        durationMs,
        tokenCost,
        createdAt,
        finishedAt
    );
  }

  void complete(long durationMs, long tokenCost) {
    this.status = AnalysisJobStatus.COMPLETED;
    this.durationMs = durationMs;
    this.tokenCost = tokenCost;
    this.finishedAt = LocalDateTime.now();
  }

  void timeout() {
    status = AnalysisJobStatus.TIMED_OUT;
    failureReason = "analysis timeout";
    finishedAt = LocalDateTime.now();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
