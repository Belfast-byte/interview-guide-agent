package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentTurnRepository
    extends JpaRepository<AdaptiveAgentTurnEntity, Long> {

  Optional<AdaptiveAgentTurnEntity> findBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  List<AdaptiveAgentTurnEntity> findBySessionIdOrderByTurnIndex(String sessionId);
}
