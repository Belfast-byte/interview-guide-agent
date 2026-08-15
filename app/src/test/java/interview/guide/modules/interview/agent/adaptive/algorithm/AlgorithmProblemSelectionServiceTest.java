package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmProblemSelectionServiceTest {

  @Mock
  private AlgorithmPersistenceService persistenceService;

  @Test
  @DisplayName("同题变体选择排除本会话已经执行过的题目")
  void shouldExcludeAttemptedVariants() {
    AlgorithmProblem seed = problem("two-sum-a");
    AlgorithmProblem available = problem("two-sum-b");
    when(persistenceService.getProblem("two-sum-a")).thenReturn(seed);
    when(persistenceService.attemptedProblemIds("session-1"))
        .thenReturn(List.of("two-sum-a"));
    when(persistenceService.findVariants("two-sum", AlgorithmDifficulty.EASY))
        .thenReturn(List.of(seed, available));
    AlgorithmProblemSelectionService service = new AlgorithmProblemSelectionService(
        persistenceService
    );

    assertThat(service.selectVariant("session-1", "two-sum-a"))
        .isEqualTo(available);
  }

  @Test
  @DisplayName("所有同题变体都已考察时明确拒绝重复出题")
  void shouldRejectWhenAllVariantsWereAttempted() {
    AlgorithmProblem seed = problem("two-sum-a");
    when(persistenceService.getProblem("two-sum-a")).thenReturn(seed);
    when(persistenceService.attemptedProblemIds("session-1"))
        .thenReturn(List.of("two-sum-a"));
    when(persistenceService.findVariants("two-sum", AlgorithmDifficulty.EASY))
        .thenReturn(List.of(seed));
    AlgorithmProblemSelectionService service = new AlgorithmProblemSelectionService(
        persistenceService
    );

    assertThatThrownBy(() -> service.selectVariant("session-1", "two-sum-a"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("没有可用");
  }

  private AlgorithmProblem problem(String id) {
    return new AlgorithmProblem(
        id,
        "两数之和变体",
        "题干",
        AlgorithmDifficulty.EASY,
        "array,hash",
        "cases/" + id + "/sample.json",
        "cases/" + id + "/hidden.json",
        2_000,
        262_144,
        "two-sum"
    );
  }
}
