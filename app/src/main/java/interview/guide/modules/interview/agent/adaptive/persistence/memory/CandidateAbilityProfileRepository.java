package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import jakarta.persistence.LockModeType;
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
      SELECT (COUNT(profile) > 0)
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.sourceSessionId = :sessionId
        AND profile.skillId = :#{#topic.skillId}
        AND profile.focusId = :#{#topic.focusId}
        AND profile.revisionReason = :reason
      """)
  boolean existsBySource(
      String sessionId,
      TopicKey topic,
      AbilityProfileRevisionReason reason
  );

  List<CandidateAbilityProfileEntity>
      findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(String candidateId);

  List<CandidateAbilityProfileEntity>
      findByTenantIdAndCandidateIdOrderByCreatedAtAscIdAsc(
          String tenantId,
          String candidateId
      );
}
