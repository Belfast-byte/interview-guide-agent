package interview.guide.modules.interview.agent.adaptive.persistence.session;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AdaptiveAgentToolCallRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentToolCallRepository
    extends JpaRepository<AdaptiveAgentToolCallEntity, Long> {

  List<AdaptiveAgentToolCallEntity> findBySessionIdAndTurnIndexAndResultIdIn(
      String sessionId,
      int turnIndex,
      Set<String> resultIds
  );
}
