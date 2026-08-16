package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 项目摘要仓储。
 */
interface ProjectDigestRepository extends JpaRepository<ProjectDigestEntity, String> {

  Optional<ProjectDigestEntity> findByRepositoryId(String repositoryId);
}
