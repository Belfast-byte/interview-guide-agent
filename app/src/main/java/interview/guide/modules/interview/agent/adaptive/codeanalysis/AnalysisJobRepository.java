package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, String> {

  Optional<AnalysisJobEntity> findByIdAndSessionId(String id, String sessionId);

  Optional<AnalysisJobEntity> findTopByRepositoryIdOrderByCreatedAtDesc(String repositoryId);

  Optional<AnalysisJobEntity> findTopBySessionIdAndStatusOrderByCreatedAtDesc(
      String sessionId,
      AnalysisJobStatus status
  );
}
