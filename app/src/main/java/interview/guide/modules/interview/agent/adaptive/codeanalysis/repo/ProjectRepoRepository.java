package interview.guide.modules.interview.agent.adaptive.codeanalysis.repo;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 项目仓库仓储。
 */
public interface ProjectRepoRepository extends JpaRepository<ProjectRepoEntity, String> {

  Optional<ProjectRepoEntity> findBySessionIdAndCommitHash(String sessionId, String commitHash);

  List<ProjectRepoEntity> findByExpiresAtBefore(LocalDateTime cutoff);
}
