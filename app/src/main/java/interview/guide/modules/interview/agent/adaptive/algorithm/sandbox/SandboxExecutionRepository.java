package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 沙箱执行仓储。
 */
public interface SandboxExecutionRepository extends JpaRepository<SandboxExecutionEntity, String> {

  /**
   * 统计消耗了候选人配额的有效执行：排除已过期（superseded）、排队超时降级与 IE 基础设施失败。
   */
  @Query("select count(execution) from SandboxExecutionEntity execution "
      + "where execution.sessionId = :sessionId and execution.supersededBy is null "
      + "and (execution.status in :activeStatuses "
      + "or (execution.status = :doneStatus and execution.verdict <> :infraFailure))")
  long countQuotaConsumingBySessionId(
      String sessionId,
      List<SandboxExecutionStatus> activeStatuses,
      SandboxExecutionStatus doneStatus,
      SandboxVerdict infraFailure
  );

  /**
   * 按工作负载类型统计有效执行，口径同 {@link #countQuotaConsumingBySessionId}。
   */
  @Query("select count(execution) from SandboxExecutionEntity execution "
      + "where execution.sessionId = :sessionId and execution.workloadType = :workloadType "
      + "and execution.supersededBy is null "
      + "and (execution.status in :activeStatuses "
      + "or (execution.status = :doneStatus and execution.verdict <> :infraFailure))")
  long countQuotaConsumingBySessionIdAndWorkloadType(
      String sessionId,
      SandboxWorkloadType workloadType,
      List<SandboxExecutionStatus> activeStatuses,
      SandboxExecutionStatus doneStatus,
      SandboxVerdict infraFailure
  );

  long countByStatus(SandboxExecutionStatus status);

  @Query("select distinct execution.problemId from SandboxExecutionEntity execution "
      + "where execution.sessionId = :sessionId and execution.workloadType = :workloadType")
  List<String> findDistinctProblemIdsBySessionIdAndWorkloadType(
      String sessionId,
      SandboxWorkloadType workloadType
  );

  Optional<SandboxExecutionEntity> findTopBySessionIdOrderBySubmissionSeqDesc(String sessionId);

  Optional<SandboxExecutionEntity> findByIdAndSessionId(String id, String sessionId);

  Optional<SandboxExecutionEntity> findTopBySessionIdAndTurnIdOrderBySubmissionSeqDesc(
      String sessionId,
      long turnId
  );

  List<SandboxExecutionEntity> findBySessionIdAndTurnIdAndSupersededByIsNull(
      String sessionId,
      long turnId
  );

  List<SandboxExecutionEntity> findByStatusAndCreatedAtBefore(
      SandboxExecutionStatus status,
      LocalDateTime cutoff
  );

  List<SandboxExecutionEntity> findByStatusAndStartedAtBefore(
      SandboxExecutionStatus status,
      LocalDateTime cutoff
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SandboxExecutionEntity> findLockedById(String id);
}
