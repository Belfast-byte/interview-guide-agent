package interview.guide.modules.interview.agent.adaptive.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveAgentToolCallRepository
    extends JpaRepository<AdaptiveAgentToolCallEntity, Long> {

  List<AdaptiveAgentToolCallEntity> findBySessionIdOrderByTurnIndexAscIdAsc(String sessionId);
}
