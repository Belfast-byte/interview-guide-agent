package interview.guide.modules.interview.agent.adaptive.persistence.working;

import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 已应用 WorkState Patch 的审计记录。 */
@Entity
@Table(
    name = "agent_work_state_patches",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_work_state_patch_source",
        columnNames = {"session_id", "source_type", "source_id"}
    )
)
public class AdaptiveWorkStatePatchEntity {

  @Id
  @Column(name = "patch_id", length = 64)
  private String patchId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "base_revision", nullable = false)
  private long baseRevision;

  @Column(name = "result_revision", nullable = false)
  private long resultRevision;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 24)
  private WorkStatePatchSource sourceType;

  @Column(name = "source_id", nullable = false, length = 128)
  private String sourceId;

  @Column(name = "operations_json", nullable = false, columnDefinition = "TEXT")
  private String operationsJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected AdaptiveWorkStatePatchEntity() {}

  public AdaptiveWorkStatePatchEntity(WorkStatePatch patch, WorkStateJsonCodec codec) {
    patchId = patch.patchId();
    sessionId = patch.sessionId();
    baseRevision = patch.baseRevision();
    resultRevision = patch.resultRevision();
    sourceType = patch.sourceType();
    sourceId = patch.sourceId();
    operationsJson = codec.encodeOperations(patch.operations());
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
