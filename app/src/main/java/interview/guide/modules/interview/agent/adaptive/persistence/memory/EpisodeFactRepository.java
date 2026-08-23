package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EpisodeFact 数据访问接口。
 */
public interface EpisodeFactRepository extends JpaRepository<EpisodeFactEntity, Long> {

  Optional<EpisodeFactEntity> findBySessionIdAndTurnIndex(
      String sessionId,
      int turnIndex
  );

  long countBySessionId(String sessionId);

  List<EpisodeFactEntity> findByTenantIdIsNullAndCandidateIdOrderByCreatedAtDescIdDesc(
      String candidateId
  );

  List<EpisodeFactEntity> findByTenantIdAndCandidateIdOrderByCreatedAtDescIdDesc(
      String tenantId,
      String candidateId
  );
}
