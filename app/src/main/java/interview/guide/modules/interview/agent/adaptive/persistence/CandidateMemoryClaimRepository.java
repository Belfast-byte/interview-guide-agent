package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
