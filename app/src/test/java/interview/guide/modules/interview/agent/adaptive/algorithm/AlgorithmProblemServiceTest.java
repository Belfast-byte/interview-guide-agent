package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.infrastructure.file.FileStorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmProblemServiceTest {

  @Mock
  private AlgorithmPersistenceService persistenceService;

  @Mock
  private FileStorageService fileStorageService;

  @Mock
  private AlgorithmProblemSelectionService selectionService;

  @Test
  @DisplayName("候选人题面只读取公开样例且不接触隐藏用例文件")
  void shouldExposeOnlyPublicProblemFacts() {
    AlgorithmProblem problem = new AlgorithmProblem(
        "two-sum-a",
        "两数之和",
        "题干",
        AlgorithmDifficulty.EASY,
        "array,hash",
        "cases/two-sum-a/sample.json",
        "cases/two-sum-a/hidden.json",
        2_000,
        262_144,
        "two-sum"
    );
    when(persistenceService.getProblem("two-sum-a")).thenReturn(problem);
    when(fileStorageService.downloadFile(problem.sampleCasesRef()))
        .thenReturn("sample cases".getBytes(StandardCharsets.UTF_8));
    AlgorithmProblemService service = new AlgorithmProblemService(
        persistenceService,
        fileStorageService,
        selectionService
    );

    PublicAlgorithmProblem result = service.getPublicProblem("two-sum-a");

    assertThat(result.sampleCases()).isEqualTo("sample cases");
    verify(fileStorageService).downloadFile(problem.sampleCasesRef());
    verify(fileStorageService, never()).downloadFile(problem.hiddenCasesRef());
  }
}
