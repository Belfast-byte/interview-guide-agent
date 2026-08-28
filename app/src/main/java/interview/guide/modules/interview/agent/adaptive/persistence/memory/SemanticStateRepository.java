package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticStateKey;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemanticStateRepository extends JpaRepository<SemanticStateEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT state
      FROM SemanticStateEntity state
      WHERE state.candidateId = :#{#key.owner.candidateId}
        AND ((:#{#key.owner.tenantId} IS NULL AND state.tenantId IS NULL)
          OR state.tenantId = :#{#key.owner.tenantId})
        AND state.skillId = :#{#key.topic.skillId}
        AND state.focusId = :#{#key.topic.focusId}
        AND state.track = :#{#key.track}
      """)
  Optional<SemanticStateEntity> findLocked(@Param("key") SemanticStateKey key);

  @Query("""
      SELECT state
      FROM SemanticStateEntity state
      WHERE state.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND state.tenantId IS NULL)
          OR state.tenantId = :#{#owner.tenantId})
      ORDER BY state.skillId, state.focusId, state.track
      """)
  List<SemanticStateEntity> findByOwner(@Param("owner") MemoryOwner owner);
}
