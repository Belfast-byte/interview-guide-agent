package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 不可变能力画像快照仓储。
 */
public interface CandidateAbilityProfileRepository
    extends JpaRepository<CandidateAbilityProfileEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT profile
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.tenantId IS NULL
        AND profile.candidateId = :candidateId
        AND profile.skillId = :#{#topic.skillId}
        AND profile.focusId = :#{#topic.focusId}
        AND profile.supersededAt IS NULL
      """)
  Optional<CandidateAbilityProfileEntity>
      findCurrentCandidateProfile(
          String candidateId,
          TopicKey topic
      );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT profile
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.tenantId = :#{#owner.tenantId}
        AND profile.candidateId = :#{#owner.candidateId}
        AND profile.skillId = :#{#topic.skillId}
        AND profile.focusId = :#{#topic.focusId}
        AND profile.supersededAt IS NULL
      """)
  Optional<CandidateAbilityProfileEntity>
      findCurrentTenantProfile(
          MemoryOwner owner,
          TopicKey topic
      );

  @Query("""
      SELECT profile
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND profile.tenantId IS NULL)
             OR profile.tenantId = :#{#owner.tenantId})
        AND profile.sourceSessionId = :sessionId
        AND profile.revisionReason = :reason
      """)
  List<CandidateAbilityProfileEntity> findProfilesBySource(
      MemoryOwner owner,
      String sessionId,
      AbilityProfileRevisionReason reason
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT profile
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND profile.tenantId IS NULL)
             OR profile.tenantId = :#{#owner.tenantId})
        AND profile.skillId IN :skillIds
        AND profile.focusId IN :focusIds
        AND profile.supersededAt IS NULL
      ORDER BY profile.skillId, profile.focusId
      """)
  List<CandidateAbilityProfileEntity> findCurrentProfiles(
      MemoryOwner owner,
      Collection<String> skillIds,
      Collection<String> focusIds
  );

  List<CandidateAbilityProfileEntity>
      findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(String candidateId);

  List<CandidateAbilityProfileEntity>
      findByTenantIdAndCandidateIdOrderByCreatedAtAscIdAsc(
          String tenantId,
          String candidateId
      );
}
