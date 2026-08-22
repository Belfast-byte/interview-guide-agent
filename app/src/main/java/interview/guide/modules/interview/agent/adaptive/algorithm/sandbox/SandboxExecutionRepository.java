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

  long countBySessionId(String sessionId);

  long countBySessionIdAndWorkloadType(
      String sessionId,
      SandboxWorkloadType workloadType
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
