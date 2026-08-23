package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * AdaptiveAgentSessionRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentSessionRepository
    extends JpaRepository<AdaptiveAgentSessionEntity, String> {

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantId(String id, String tenantId);

  Optional<AdaptiveAgentSessionEntity> findByIdAndTenantIdIsNull(String id);

  Optional<AdaptiveAgentSessionEntity> findByIdAndCandidateIdAndTenantIdIsNull(
      String id,
      String candidateId
  );

  boolean existsByLlmProviderAndTenantIdIsNullAndStatusIn(
      String llmProvider,
      Collection<AdaptiveSessionStatus> statuses
  );

  @Query(
      value = """
          SELECT s.id AS sessionId, s.status AS status,
                 s.currentTurn AS currentTurn, s.maxTurns AS maxTurns,
                 s.jd AS jd, s.createdAt AS createdAt, s.completedAt AS completedAt
          FROM AdaptiveAgentSessionEntity s
          WHERE s.candidateId = :candidateId AND s.tenantId IS NULL
          ORDER BY s.createdAt DESC
          """,
      countQuery = """
          SELECT COUNT(s)
          FROM AdaptiveAgentSessionEntity s
          WHERE s.candidateId = :candidateId AND s.tenantId IS NULL
          """
  )
  Page<AdaptiveInterviewSummaryProjection> findCandidateHistory(
      @Param("candidateId") String candidateId,
      Pageable pageable
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdaptiveAgentSessionEntity> findLockedByIdAndTenantIdIsNull(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdaptiveAgentSessionEntity> findLockedById(String id);
}
