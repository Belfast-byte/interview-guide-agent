package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectDigestRepository extends JpaRepository<ProjectDigestEntity, String> {

  Optional<ProjectDigestEntity> findByRepositoryId(String repositoryId);
}
