package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 候选人报告使用的标签聚合只读查询。 */
public interface CandidateMemoryTagQueryRepository
    extends Repository<EpisodeTagEntity, Long> {

  @Query("""
      SELECT episode.skillId AS skillId,
             episode.focusId AS focusId,
             tag.category AS category,
             tag.tag AS tag,
             COUNT(tag.id) AS tagCount
      FROM EpisodeTagEntity tag
      JOIN tag.episode episode
      WHERE episode.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND episode.tenantId IS NULL)
             OR episode.tenantId = :#{#owner.tenantId})
      GROUP BY episode.skillId, episode.focusId, tag.category, tag.tag
      ORDER BY episode.skillId ASC, episode.focusId ASC,
               tag.category ASC, tag.tag ASC
      """)
  List<CandidateMemoryTagCountProjection> countByOwner(MemoryOwner owner);
}
