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

  @Query("""
      SELECT e.id AS episodeId,
             e.skillId AS skillId,
             e.focusId AS focusId,
             assessment.depthLevel AS depthLevel,
             e.createdAt AS createdAt
      FROM EpisodeFactEntity e,
           AdaptiveAgentSessionEntity history,
           AdaptiveAgentSessionEntity current
      JOIN e.assessment assessment
      WHERE current.id = :currentSessionId
        AND history.id = e.sessionId
        AND history.status = interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus.COMPLETED
        AND e.sessionId <> :currentSessionId
        AND e.skillId = :skillId
        AND history.candidateId = current.candidateId
        AND e.candidateId = current.candidateId
        AND ((history.tenantId IS NULL AND current.tenantId IS NULL)
             OR history.tenantId = current.tenantId)
        AND ((e.tenantId IS NULL AND current.tenantId IS NULL)
             OR e.tenantId = current.tenantId)
      ORDER BY e.createdAt DESC, e.id DESC
      """)
  List<EpisodePromptFactProjection> findCompletedPromptFacts(
      @Param("currentSessionId") String currentSessionId,
      @Param("skillId") String skillId
  );
}
