package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 项目仓库仓储。
 */
interface ProjectRepoRepository extends JpaRepository<ProjectRepoEntity, String> {

  Optional<ProjectRepoEntity> findBySessionIdAndCommitHash(String sessionId, String commitHash);

  List<ProjectRepoEntity> findByExpiresAtBefore(LocalDateTime cutoff);
}
