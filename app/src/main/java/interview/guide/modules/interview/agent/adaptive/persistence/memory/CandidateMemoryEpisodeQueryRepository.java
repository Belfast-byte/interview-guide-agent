package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 候选人报告使用的 Episode 安全投影查询。 */
public interface CandidateMemoryEpisodeQueryRepository
    extends Repository<EpisodeFactEntity, Long> {

  @Query(
      value = """
          SELECT episode.sessionId AS sessionId,
                 episode.turnIndex AS turnIndex,
                 turn.parentTurnIndex AS parentTurnIndex,
                 episode.skillId AS skillId,
                 episode.focusId AS focusId,
                 assessment.depthLevel AS depthLevel,
                 episode.enrichmentStatus AS enrichmentStatus,
                 episode.createdAt AS createdAt
          FROM EpisodeFactEntity episode,
               AdaptiveAgentTurnEntity turn
          JOIN episode.assessment assessment
          WHERE turn.sessionId = episode.sessionId
            AND turn.turnIndex = episode.turnIndex
            AND episode.candidateId = :#{#owner.candidateId}
            AND ((:#{#owner.tenantId} IS NULL AND episode.tenantId IS NULL)
                 OR episode.tenantId = :#{#owner.tenantId})
          ORDER BY episode.createdAt DESC, episode.id DESC
          """,
      countQuery = """
          SELECT COUNT(episode)
          FROM EpisodeFactEntity episode,
               AdaptiveAgentTurnEntity turn
          WHERE turn.sessionId = episode.sessionId
            AND turn.turnIndex = episode.turnIndex
            AND episode.candidateId = :#{#owner.candidateId}
            AND ((:#{#owner.tenantId} IS NULL AND episode.tenantId IS NULL)
                 OR episode.tenantId = :#{#owner.tenantId})
          """
  )
  Page<CandidateMemoryEpisodeProjection> findByOwner(
      MemoryOwner owner,
      Pageable pageable
  );
}
