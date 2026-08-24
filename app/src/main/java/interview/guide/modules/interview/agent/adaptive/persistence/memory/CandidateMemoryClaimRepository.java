package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.UnverifiedClaim;
import interview.guide.modules.interview.agent.adaptive.memory.profile.UnverifiedClaimSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CandidateMemoryClaimRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface CandidateMemoryClaimRepository
    extends JpaRepository<CandidateMemoryClaimEntity, Long>, UnverifiedClaimSource {

  List<CandidateMemoryClaimEntity> findByTenantIdAndCandidateIdOrderByObservedAtDesc(
      String tenantId,
      String candidateId
  );

  List<CandidateMemoryClaimEntity> findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(
      String candidateId
  );

  @Override
  default List<UnverifiedClaim> findUnverifiedClaims(MemoryOwner owner) {
    List<CandidateMemoryClaimEntity> claims = owner.tenantId() == null
        ? findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(owner.candidateId())
        : findByTenantIdAndCandidateIdOrderByObservedAtDesc(
            owner.tenantId(),
            owner.candidateId()
        );
    return claims.stream()
        .map(CandidateMemoryClaimEntity::toDomain)
        .distinct()
        .toList();
  }
}
