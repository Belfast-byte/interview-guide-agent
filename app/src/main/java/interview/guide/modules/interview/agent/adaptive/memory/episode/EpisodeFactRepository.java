package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 回答幂等由原提交事务及 session/turn 唯一约束负责。 */
public interface EpisodeFactRepository extends JpaRepository<EpisodeFactEntity, Long> {
  Optional<EpisodeFactEntity> findBySessionIdAndTurnIndex(String sessionId, int turnIndex);
  long countBySessionId(String sessionId);
}
