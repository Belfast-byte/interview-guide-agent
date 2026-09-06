package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import interview.guide.modules.interview.agent.adaptive.core.context.*;

public interface MemoryObservationRepository extends JpaRepository<MemoryObservationRevision,Long> {
  @Query("""
      select r from MemoryObservationRevision r
      where r.candidateId=:#{#owner.candidateId}
      and ((:#{#owner.tenantId} is null and r.tenantId is null) or r.tenantId=:#{#owner.tenantId})
      and r.skillId=:#{#topic.skillId} and r.focusId=:#{#topic.focusId}
      and r.revision=(select max(v.revision) from MemoryObservationRevision v where v.episodeId=r.episodeId)
      order by r.episodeId, r.id
      """)
  List<MemoryObservationRevision> current(@Param("owner") MemoryOwner owner,
      @Param("topic") TopicKey topic, Pageable page);
  List<MemoryObservationRevision> findByEpisodeIdOrderByRevisionDesc(long episodeId);
}
