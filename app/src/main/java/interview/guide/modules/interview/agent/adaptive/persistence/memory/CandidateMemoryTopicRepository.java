package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.CoveredTopic;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CoveredTopicSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CandidateMemoryTopicRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface CandidateMemoryTopicRepository
    extends JpaRepository<CandidateMemoryTopicEntity, Long>, CoveredTopicSource {

  List<CandidateMemoryTopicEntity> findByTenantIdAndCandidateIdOrderByObservedAtDesc(
      String tenantId,
      String candidateId
  );

  List<CandidateMemoryTopicEntity> findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(
      String candidateId
  );

  @Override
  default List<CoveredTopic> findCoveredTopics(MemoryOwner owner) {
    List<CandidateMemoryTopicEntity> topics = owner.tenantId() == null
        ? findByTenantIdIsNullAndCandidateIdOrderByObservedAtDesc(owner.candidateId())
        : findByTenantIdAndCandidateIdOrderByObservedAtDesc(
            owner.tenantId(),
            owner.candidateId()
        );
    return topics.stream()
        .map(CandidateMemoryTopicEntity::toDomain)
        .distinct()
        .toList();
  }
}
