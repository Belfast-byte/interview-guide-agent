package interview.guide.modules.interview.agent.adaptive.persistence.assessment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdaptiveAgentAssessmentRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentAssessmentRepository
    extends JpaRepository<AdaptiveAgentAssessmentEntity, Long> {

  List<AdaptiveAgentAssessmentEntity>
      findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(String sessionId);

  Optional<AdaptiveAgentAssessmentEntity>
      findTopBySessionIdAndDimensionOrderOrderByTurnIndexDesc(
          String sessionId,
          int dimensionOrder
      );

  Optional<AdaptiveAgentAssessmentEntity> findBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );
}
