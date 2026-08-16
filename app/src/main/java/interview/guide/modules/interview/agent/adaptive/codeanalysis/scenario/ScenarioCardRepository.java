package interview.guide.modules.interview.agent.adaptive.codeanalysis.scenario;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 场景卡片仓储。
 */
public interface ScenarioCardRepository extends JpaRepository<ScenarioCardEntity, Long> {

  List<ScenarioCardEntity> findByRepositoryIdOrderByScenarioId(String repositoryId);

  Optional<ScenarioCardEntity> findByRepositoryIdAndScenarioId(
      String repositoryId,
      String scenarioId
  );
}
