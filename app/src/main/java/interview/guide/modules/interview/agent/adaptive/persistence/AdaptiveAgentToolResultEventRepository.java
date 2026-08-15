package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdaptiveAgentToolResultEventRepository
    extends JpaRepository<AdaptiveAgentToolResultEventEntity, Long> {

  boolean existsByToolNameAndResultId(String toolName, String resultId);

  Optional<AdaptiveAgentToolResultEventEntity> findByToolNameAndResultId(
      String toolName,
      String resultId
  );

  List<AdaptiveAgentToolResultEventEntity> findBySessionIdAndStatusOrderById(
      String sessionId,
      ToolResultEventStatus status
  );
}
