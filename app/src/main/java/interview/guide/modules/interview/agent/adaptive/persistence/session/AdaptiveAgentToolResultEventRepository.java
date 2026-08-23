package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeToolResultFact;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * AdaptiveAgentToolResultEventRepository 数据访问接口，提供相关实体的 Spring Data Repository。
 */
public interface AdaptiveAgentToolResultEventRepository
    extends JpaRepository<AdaptiveAgentToolResultEventEntity, Long> {

  boolean existsByToolNameAndResultId(String toolName, String resultId);

  Optional<AdaptiveAgentToolResultEventEntity> findByToolNameAndResultId(
      String toolName,
      String resultId
  );

  Optional<AdaptiveAgentToolResultEventEntity> findBySessionIdAndToolNameAndResultId(
      String sessionId,
      String toolName,
      String resultId
  );

  List<AdaptiveAgentToolResultEventEntity> findBySessionIdAndStatusOrderById(
      String sessionId,
      ToolResultEventStatus status
  );

  @Query("""
      select event.resultOutput
      from AdaptiveAgentToolResultEventEntity event
      where event.sessionId = :sessionId and event.turnIndex = :turnIndex
      order by event.id desc
      """)
  List<String> findResultOutputsBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  @Query("""
      select new interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeToolResultFact(
        event.id, event.resultSummary, event.resultOutput
      )
      from AdaptiveAgentToolResultEventEntity event
      where event.sessionId = :sessionId and event.turnIndex = :turnIndex
      order by event.id
      """)
  List<EpisodeToolResultFact> findEpisodeFactsBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );
}
