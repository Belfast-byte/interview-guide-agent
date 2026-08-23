package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  /**
   * 生成 Profile 的主题必有 Counter；稳定排序并锁行可串行化同主题的并发快照。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT counter
      FROM AbilityCounterEntity counter
      WHERE counter.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND counter.tenantId IS NULL)
             OR counter.tenantId = :#{#owner.tenantId})
        AND counter.skillId IN :skillIds
        AND counter.focusId IN :focusIds
      ORDER BY counter.skillId, counter.focusId
      """)
  List<AbilityCounterEntity> findCounters(
      MemoryOwner owner,
      Collection<String> skillIds,
      Collection<String> focusIds
  );
}
