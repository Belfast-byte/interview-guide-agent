package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EpisodeFact 数据访问接口。
 */
public interface EpisodeFactRepository extends JpaRepository<EpisodeFactEntity, Long> {

  Optional<EpisodeFactEntity> findBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EpisodeFactEntity> findLockedById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<EpisodeFactEntity> findByEnrichmentStatusAndUpdatedAtBeforeOrderByUpdatedAtAscIdAsc(
      EpisodeEnrichmentStatus status,
      LocalDateTime cutoff
  );

  long countBySessionId(String sessionId);

  List<EpisodeFactEntity> findByTenantIdIsNullAndCandidateIdOrderByCreatedAtDescIdDesc(
      String candidateId
  );

  List<EpisodeFactEntity> findByTenantIdAndCandidateIdOrderByCreatedAtDescIdDesc(
      String tenantId,
      String candidateId
  );
}
