package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CandidateMemoryTopicRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
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
