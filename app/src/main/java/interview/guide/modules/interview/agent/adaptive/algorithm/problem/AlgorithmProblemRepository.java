package interview.guide.modules.interview.agent.adaptive.algorithm.problem;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 算法题目仓储。
 */
public interface AlgorithmProblemRepository extends JpaRepository<AlgorithmProblemEntity, String> {

  List<AlgorithmProblemEntity> findByVariantGroupAndDifficultyOrderById(
      String variantGroup,
      AlgorithmDifficulty difficulty
  );
}
