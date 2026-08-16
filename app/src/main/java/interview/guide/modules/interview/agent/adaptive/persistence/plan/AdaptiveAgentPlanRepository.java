package interview.guide.modules.interview.agent.adaptive.persistence.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdaptiveAgentPlanRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentPlanRepository
    extends JpaRepository<AdaptiveAgentPlanEntity, Long> {

  List<AdaptiveAgentPlanEntity> findBySessionIdOrderByDimensionOrder(String sessionId);
}
