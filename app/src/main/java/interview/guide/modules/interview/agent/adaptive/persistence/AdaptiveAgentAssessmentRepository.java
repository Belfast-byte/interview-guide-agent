package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentAssessmentRepository
    extends JpaRepository<AdaptiveAgentAssessmentEntity, Long> {

  List<AdaptiveAgentAssessmentEntity>
      findBySessionIdOrderByDimensionOrderAscTurnIndexAsc(String sessionId);

  Optional<AdaptiveAgentAssessmentEntity>
      findTopBySessionIdAndDimensionOrderOrderByTurnIndexDesc(
          String sessionId,
          int dimensionOrder
      );
}
