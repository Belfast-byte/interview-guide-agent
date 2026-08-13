package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveDimensionBriefRepository
    extends JpaRepository<AdaptiveDimensionBriefEntity, Long> {

  @EntityGraph(attributePaths = "turnIndexes")
  List<AdaptiveDimensionBriefEntity> findBySessionIdOrderByDimensionOrder(String sessionId);
}
