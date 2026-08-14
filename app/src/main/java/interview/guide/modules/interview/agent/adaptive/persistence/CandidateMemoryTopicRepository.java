package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateMemoryTopicRepository
    extends JpaRepository<CandidateMemoryTopicEntity, Long> {

  List<CandidateMemoryTopicEntity> findByTenantIdAndCandidateIdOrderByObservedAtDesc(
      String tenantId,
      String candidateId
  );

  List<CandidateMemoryTopicEntity> findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(
      String candidateId
  );
}
