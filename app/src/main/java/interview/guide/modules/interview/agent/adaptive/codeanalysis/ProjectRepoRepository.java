package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepoRepository extends JpaRepository<ProjectRepoEntity, String> {

  Optional<ProjectRepoEntity> findBySessionIdAndCommitHash(String sessionId, String commitHash);
}
