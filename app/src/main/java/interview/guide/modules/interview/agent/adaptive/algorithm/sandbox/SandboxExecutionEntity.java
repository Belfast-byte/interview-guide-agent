package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 沙箱执行 JPA 实体。
 */
@Entity
@Table(name = "sandbox_executions")
public class SandboxExecutionEntity {

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "turn_id", nullable = false)
  private long turnId;

  @Column(name = "submission_seq", nullable = false)
  private int submissionSeq;

  @Enumerated(EnumType.STRING)
  @Column(name = "workload_type", nullable = false, length = 16)
  private SandboxWorkloadType workloadType;

  @Column(name = "problem_id", length = 64)
  private String problemId;

  @Column(name = "scenario_id", length = 64)
  private String scenarioId;

  @Column(name = "workspace_ref", length = 512)
  private String workspaceRef;

  @Column(name = "tests_ref", length = 512)
  private String testsRef;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SandboxLanguage language;

  @Column(name = "code_ref", nullable = false, length = 512)
  private String codeRef;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "run_mode", nullable = false, length = 16)
  private SandboxRunMode runMode;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private SandboxVerdict verdict;

  private Integer passed;

  private Integer total;

  @Column(name = "time_ms")
  private Long timeMs;

  @Column(name = "memory_kb")
  private Long memoryKb;

  @Column(name = "first_failed_case")
  private Integer firstFailedCase;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy_violation", length = 32)
  private SandboxPolicyViolation policyViolation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private SandboxExecutionStatus status;

  @Column(name = "superseded_by", length = 36)
  private String supersededBy;

  @Column(name = "pending_rejudge", nullable = false)
  private boolean pendingRejudge;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  protected SandboxExecutionEntity() {}

  public SandboxExecutionEntity(
      String id,
      CreateSandboxExecution command,
      long turnId,
      int submissionSeq
  ) {
    this.id = id;
    sessionId = command.sessionId();
    this.turnId = turnId;
    this.submissionSeq = submissionSeq;
    workloadType = command.workloadType();
    problemId = command.problemId();
    scenarioId = command.scenarioId();
    workspaceRef = command.workspaceRef();
    testsRef = command.testsRef();
    language = command.language();
    codeRef = command.codeRef();
    codeHash = command.codeHash();
    runMode = command.runMode();
    status = SandboxExecutionStatus.PENDING;
  }

  public boolean markRunning() {
    if (status != SandboxExecutionStatus.PENDING) {
      return false;
    }
    status = SandboxExecutionStatus.RUNNING;
    startedAt = LocalDateTime.now();
    return true;
  }

  public void supersedeWith(String executionId) {
    supersededBy = executionId;
  }

  public boolean hasDifferentCode(String hash) {
    return !codeHash.equals(hash);
  }

  public boolean apply(SandboxExecutionResult result) {
    if (result.verdict() == SandboxVerdict.IE && retryCount == 0) {
      retryCount = 1;
      status = SandboxExecutionStatus.PENDING;
      startedAt = null;
      return true;
    }
    verdict = result.verdict();
    passed = result.passed();
    total = result.total();
    timeMs = result.timeMs();
    memoryKb = result.memoryKb();
    firstFailedCase = result.firstFailedCase();
    policyViolation = result.policyViolation();
    status = SandboxExecutionStatus.DONE;
    pendingRejudge = verdict == SandboxVerdict.IE;
    finishedAt = LocalDateTime.now();
    return false;
  }

  public boolean resetAfterWorkerFailure() {
    if (status != SandboxExecutionStatus.RUNNING) {
      return false;
    }
    status = SandboxExecutionStatus.PENDING;
    startedAt = null;
    return true;
  }

  public void markInfrastructureFailure() {
    verdict = SandboxVerdict.IE;
    status = SandboxExecutionStatus.DONE;
    pendingRejudge = true;
    finishedAt = LocalDateTime.now();
  }

  public boolean markQueuedTimeout() {
    if (status != SandboxExecutionStatus.PENDING) {
      return false;
    }
    status = SandboxExecutionStatus.TIMEOUT_QUEUED;
    finishedAt = LocalDateTime.now();
    return true;
  }

  public boolean markStuckRunningTimeout() {
    if (status != SandboxExecutionStatus.RUNNING) {
      return false;
    }
    verdict = SandboxVerdict.IE;
    status = SandboxExecutionStatus.TIMEOUT_QUEUED;
    pendingRejudge = true;
    finishedAt = LocalDateTime.now();
    return true;
  }

  public SandboxExecution toDomain() {
    return new SandboxExecution(
        id,
        sessionId,
        turnId,
        submissionSeq,
        workloadType,
        problemId,
        scenarioId,
        workspaceRef,
        testsRef,
        language,
        codeRef,
        codeHash,
        runMode,
        status,
        verdict,
        passed,
        total,
        timeMs,
        memoryKb,
        firstFailedCase,
        supersededBy,
        pendingRejudge,
        retryCount,
        createdAt,
        finishedAt,
        policyViolation
    );
  }

  public String id() {
    return id;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }
}
