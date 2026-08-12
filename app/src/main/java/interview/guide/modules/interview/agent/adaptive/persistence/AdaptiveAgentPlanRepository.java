package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentPlanRepository
    extends JpaRepository<AdaptiveAgentPlanEntity, Long> {

  List<AdaptiveAgentPlanEntity> findBySessionIdOrderByDimensionOrder(String sessionId);
}
