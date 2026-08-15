package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_repos")
class ProjectRepoEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "repository_ref", nullable = false, length = 512)
  private String repositoryRef;

  @Column(name = "commit_hash", nullable = false, length = 64)
  private String commitHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected ProjectRepoEntity() {}

  ProjectRepoEntity(
      String id,
      String sessionId,
      String tenantId,
      String repositoryRef,
      String commitHash,
      LocalDateTime expiresAt
  ) {
    this.id = id;
    this.sessionId = sessionId;
    this.tenantId = tenantId;
    this.repositoryRef = repositoryRef;
    this.commitHash = commitHash;
    this.expiresAt = expiresAt;
  }

  String id() {
    return id;
  }

  String sessionId() {
    return sessionId;
  }

  String commitHash() {
    return commitHash;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
