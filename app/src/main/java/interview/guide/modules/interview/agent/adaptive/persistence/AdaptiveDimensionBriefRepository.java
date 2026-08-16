package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdaptiveDimensionBriefRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveDimensionBriefRepository
    extends JpaRepository<AdaptiveDimensionBriefEntity, Long> {

  @EntityGraph(attributePaths = "turnIndexes")
  List<AdaptiveDimensionBriefEntity> findBySessionIdOrderByDimensionOrder(String sessionId);
}
