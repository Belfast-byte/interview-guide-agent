package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 项目摘要实体。
 */
@Entity
@Table(name = "project_digests")
public class ProjectDigestEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "repository_id", nullable = false, length = 36)
  private String repositoryId;

  @Column(name = "commit_hash", nullable = false, length = 64)
  private String commitHash;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected ProjectDigestEntity() {}

  public ProjectDigestEntity(String id, String repositoryId, String commitHash, String payloadJson) {
    this.id = id;
    this.repositoryId = repositoryId;
    this.commitHash = commitHash;
    this.payloadJson = payloadJson;
  }

  public String payloadJson() {
    return payloadJson;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
