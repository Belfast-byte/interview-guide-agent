package interview.guide.modules.interview.agent.adaptive.algorithm;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SandboxExecutionRepository extends JpaRepository<SandboxExecutionEntity, String> {

  long countBySessionId(String sessionId);

  Optional<SandboxExecutionEntity> findTopBySessionIdOrderBySubmissionSeqDesc(String sessionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SandboxExecutionEntity> findLockedById(String id);
}
