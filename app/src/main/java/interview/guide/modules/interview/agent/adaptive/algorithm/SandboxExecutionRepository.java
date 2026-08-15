package interview.guide.modules.interview.agent.adaptive.algorithm;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SandboxExecutionRepository extends JpaRepository<SandboxExecutionEntity, String> {

  long countBySessionId(String sessionId);

  long countByStatus(SandboxExecutionStatus status);

  Optional<SandboxExecutionEntity> findTopBySessionIdOrderBySubmissionSeqDesc(String sessionId);

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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SandboxExecutionEntity> findLockedById(String id);
}
