package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScenarioCardRepository extends JpaRepository<ScenarioCardEntity, Long> {

  List<ScenarioCardEntity> findByRepositoryIdOrderByScenarioId(String repositoryId);
}
