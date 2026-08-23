package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * AbilityCounter 数据访问接口。
 */
public interface AbilityCounterRepository
    extends JpaRepository<AbilityCounterEntity, Long> {

  @Query("""
      SELECT counter
      FROM AbilityCounterEntity counter
      WHERE counter.tenantId IS NULL
        AND counter.candidateId = :candidateId
        AND counter.skillId = :#{#topic.skillId}
        AND counter.focusId = :#{#topic.focusId}
      """)
  Optional<AbilityCounterEntity> findCandidateCounter(
      String candidateId,
      TopicKey topic
  );

  @Query("""
      SELECT counter
      FROM AbilityCounterEntity counter
      WHERE counter.tenantId = :#{#owner.tenantId}
        AND counter.candidateId = :#{#owner.candidateId}
        AND counter.skillId = :#{#topic.skillId}
        AND counter.focusId = :#{#topic.focusId}
      """)
  Optional<AbilityCounterEntity> findTenantCounter(
      MemoryOwner owner,
      TopicKey topic
  );
}
