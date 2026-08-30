package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticOwnerTopic;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticTrack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemanticContributionRepository
    extends JpaRepository<SemanticContributionEntity, Long> {

  Optional<SemanticContributionEntity> findByEpisodeIdAndTrack(
      long episodeId,
      SemanticTrack track
  );

  @Query("""
      SELECT contribution
      FROM SemanticContributionEntity contribution
      WHERE contribution.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND contribution.tenantId IS NULL)
          OR contribution.tenantId = :#{#owner.tenantId})
      ORDER BY contribution.skillId, contribution.focusId, contribution.createdAt,
        contribution.id
      """)
  List<SemanticContributionEntity> findByOwner(@Param("owner") MemoryOwner owner);

  @Query("""
      SELECT contribution
      FROM SemanticContributionEntity contribution
      WHERE ((:#{#scope.owner.tenantId} IS NULL AND contribution.tenantId IS NULL)
        OR contribution.tenantId = :#{#scope.owner.tenantId})
        AND contribution.candidateId = :#{#scope.owner.candidateId}
        AND contribution.skillId = :#{#scope.topic.skillId}
        AND contribution.focusId = :#{#scope.topic.focusId}
      ORDER BY contribution.createdAt, contribution.id
      """)
  List<SemanticContributionEntity> findByOwnerAndTopic(
      @Param("scope") SemanticOwnerTopic scope);
}
