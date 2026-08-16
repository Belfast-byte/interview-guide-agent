package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CandidateAbilityProfileRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface CandidateAbilityProfileRepository
    extends JpaRepository<CandidateAbilityProfileEntity, Long> {

  Optional<CandidateAbilityProfileEntity>
      findByTenantIdIsNullAndCandidateIdAndDimensionAndSupersededByIsNull(
          String candidateId,
          String dimension
      );

  Optional<CandidateAbilityProfileEntity>
      findByTenantIdAndCandidateIdAndDimensionAndSupersededByIsNull(
          String tenantId,
          String candidateId,
          String dimension
      );

  Optional<CandidateAbilityProfileEntity> findBySourceSessionIdAndDimensionOrder(
      String sessionId,
      int dimensionOrder
  );

  List<CandidateAbilityProfileEntity>
      findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(String candidateId);
}
