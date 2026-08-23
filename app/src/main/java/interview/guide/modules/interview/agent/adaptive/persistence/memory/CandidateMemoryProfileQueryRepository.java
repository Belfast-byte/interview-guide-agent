package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 候选人报告使用的 current Profile 只读查询。 */
public interface CandidateMemoryProfileQueryRepository
    extends Repository<CandidateAbilityProfileEntity, Long> {

  @Query("""
      SELECT profile
      FROM CandidateAbilityProfileEntity profile
      WHERE profile.candidateId = :#{#owner.candidateId}
        AND ((:#{#owner.tenantId} IS NULL AND profile.tenantId IS NULL)
             OR profile.tenantId = :#{#owner.tenantId})
        AND profile.supersededAt IS NULL
      ORDER BY profile.skillId ASC, profile.focusId ASC, profile.id ASC
      """)
  List<CandidateAbilityProfileEntity> findCurrentByOwner(MemoryOwner owner);
}
