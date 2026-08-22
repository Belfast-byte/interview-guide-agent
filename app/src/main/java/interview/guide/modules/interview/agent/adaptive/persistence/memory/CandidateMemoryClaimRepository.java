package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CandidateMemoryClaimRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface CandidateMemoryClaimRepository
    extends JpaRepository<CandidateMemoryClaimEntity, Long> {

  List<CandidateMemoryClaimEntity> findByTenantIdAndCandidateIdOrderByObservedAtDesc(
      String tenantId,
      String candidateId
  );

  List<CandidateMemoryClaimEntity> findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(
      String candidateId
  );
}
