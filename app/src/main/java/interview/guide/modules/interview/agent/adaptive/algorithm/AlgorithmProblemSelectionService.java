package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlgorithmProblemSelectionService {

  private final AlgorithmPersistenceService persistenceService;

  public AlgorithmProblem selectVariant(String sessionId, String problemId) {
    AlgorithmProblem seed = persistenceService.getProblem(problemId);
    Set<String> attempted = Set.copyOf(persistenceService.attemptedProblemIds(sessionId));
    List<AlgorithmProblem> candidates = persistenceService.findVariants(
        seed.variantGroup(),
        seed.difficulty()
    ).stream()
        .filter(problem -> !attempted.contains(problem.id()))
        .toList();
    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "本会话没有可用的未考察题目变体");
    }
    int index = Math.floorMod(sessionId.hashCode(), candidates.size());
    return candidates.get(index);
  }
}
