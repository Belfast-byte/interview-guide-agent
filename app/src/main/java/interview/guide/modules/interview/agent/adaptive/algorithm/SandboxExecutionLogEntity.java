package interview.guide.modules.interview.agent.adaptive.algorithm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 沙箱执行日志实体。
 */
@Entity
@Table(name = "sandbox_execution_logs")
class SandboxExecutionLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "execution_id", nullable = false, length = 36)
  private String executionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "log_type", nullable = false, length = 24)
  private SandboxLogType logType;

  @Column(name = "storage_ref", nullable = false, length = 512)
  private String storageRef;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected SandboxExecutionLogEntity() {}

  SandboxExecutionLogEntity(String executionId, SandboxExecutionLog log) {
    this.executionId = executionId;
    logType = log.type();
    storageRef = log.storageRef();
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
