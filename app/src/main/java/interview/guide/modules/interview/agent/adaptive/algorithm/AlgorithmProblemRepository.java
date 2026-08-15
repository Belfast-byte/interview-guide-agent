package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AlgorithmProblemRepository extends JpaRepository<AlgorithmProblemEntity, String> {

  List<AlgorithmProblemEntity> findByVariantGroupAndDifficultyOrderById(
      String variantGroup,
      AlgorithmDifficulty difficulty
  );
}
