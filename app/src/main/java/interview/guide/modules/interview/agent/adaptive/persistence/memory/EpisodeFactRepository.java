package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
  Optional<EpisodeFactEntity> findLockedByIdAndCandidateIdAndTenantIdIsNull(
      Long id,
      String candidateId
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EpisodeFactEntity> findLockedByIdAndTenantIdAndCandidateId(
      Long id,
      String tenantId,
      String candidateId
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<EpisodeFactEntity> findByEnrichmentStatusAndUpdatedAtBeforeOrderByUpdatedAtAscIdAsc(
      EpisodeEnrichmentStatus status,
      LocalDateTime cutoff
  );

  @Query("""
      SELECT episode.id AS episodeId,
             session.id AS sessionId,
             session.llmProvider AS llmProvider
      FROM EpisodeFactEntity episode
      LEFT JOIN AdaptiveAgentSessionEntity session ON session.id = episode.sessionId
      WHERE episode.enrichmentStatus = :status
      ORDER BY episode.updatedAt ASC, episode.id ASC
      """)
  List<EpisodeEnrichmentJobProjection> findEnrichmentJobsByStatus(
      @Param("status") EpisodeEnrichmentStatus status
  );

  @Query("""
      SELECT episode.id AS episodeId,
             session.id AS sessionId,
             session.llmProvider AS llmProvider
      FROM EpisodeFactEntity episode
      LEFT JOIN AdaptiveAgentSessionEntity session ON session.id = episode.sessionId
      WHERE episode.id = :episodeId
      """)
  Optional<EpisodeEnrichmentJobProjection> findEnrichmentJobById(
      @Param("episodeId") long episodeId
  );

  long countBySessionId(String sessionId);

  List<EpisodeFactEntity> findByTurnIdIn(List<Long> turnIds);

  List<EpisodeFactEntity> findByTenantIdIsNullAndCandidateIdOrderByCreatedAtDescIdDesc(
      String candidateId
  );

  List<EpisodeFactEntity> findByTenantIdAndCandidateIdOrderByCreatedAtDescIdDesc(
      String tenantId,
      String candidateId
  );

}
