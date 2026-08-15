package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.infrastructure.file.FileStorageService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlgorithmProblemService {

  private final AlgorithmPersistenceService persistenceService;
  private final FileStorageService fileStorageService;
  private final AlgorithmProblemSelectionService selectionService;

  public PublicAlgorithmProblem getPublicProblem(String problemId) {
    return toPublicProblem(persistenceService.getProblem(problemId));
  }

  public PublicAlgorithmProblem selectPublicVariant(String sessionId, String problemId) {
    return toPublicProblem(selectionService.selectVariant(sessionId, problemId));
  }

  private PublicAlgorithmProblem toPublicProblem(AlgorithmProblem problem) {
    String sampleCases = new String(
        fileStorageService.downloadFile(problem.sampleCasesRef()),
        StandardCharsets.UTF_8
    );
    return new PublicAlgorithmProblem(
        problem.id(),
        problem.title(),
        problem.statement(),
        problem.difficulty(),
        problem.tags(),
        sampleCases
    );
  }
}
