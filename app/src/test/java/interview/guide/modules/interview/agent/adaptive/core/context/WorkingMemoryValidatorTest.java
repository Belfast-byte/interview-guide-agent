package interview.guide.modules.interview.agent.adaptive.core.context;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkingMemoryValidatorTest {

  private final WorkingMemoryValidator validator = new WorkingMemoryValidator();
  private final WorkingMemoryReferences references = new WorkingMemoryReferences(
      new WorkingMemoryReferences.ContextIds(
          Set.of(2),
          Set.of("target-1"),
          Set.of(11L)
      ),
      Set.of(21L, 22L),
      Set.of("observation-1")
  );

  @Test
  @DisplayName("可选单值引用仍允许为空，未采用引用使用空数组")
  void shouldAcceptEmptyMemory() {
    assertThatCode(() -> validator.validate(WorkingMemory.empty(), references))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"observation", "supporting", "contradicting"})
  @DisplayName("引用数组中的空元素在模型边界被明确拒绝")
  void shouldRejectNullReferenceElements(String field) {
    List<Long> nullIds = java.util.Collections.singletonList(null);
    var links = new WorkingMemory.EvidenceLinks(
        field.equals("supporting") ? nullIds : List.of(),
        field.equals("contradicting") ? nullIds : List.of());
    var memory = new WorkingMemory(null, WorkingMemory.empty().focus(),
        new WorkingMemory.Deliberation(
            List.of(new WorkingMemory.Hypothesis("待验证", "OPEN", links)), null,
            field.equals("observation") ? java.util.Collections.singletonList(null) : List.of()));

    assertThatThrownBy(() -> validator.validate(memory, references))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("引用数组元素不能为空");
  }

  @Test
  @DisplayName("当前 Context 中的事实引用可进入 WorkingMemory")
  void shouldAcceptReferencesFromCurrentContext() {
    WorkingMemory memory = new WorkingMemory(
        2,
        new WorkingMemory.Focus(
            "target-1",
            11L,
            List.of(new WorkingMemory.GapPriority(11L, "需要核实边界"))
        ),
        new WorkingMemory.Deliberation(
            List.of(new WorkingMemory.Hypothesis(
                "候选人可能理解并发冲突",
                "OPEN",
                new WorkingMemory.EvidenceLinks(List.of(21L), List.of(22L))
            )),
            "继续验证冲突处理",
            List.of("observation-1")
        )
    );

    assertThatCode(() -> validator.validate(memory, references)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Context 外的事实引用被明确拒绝")
  void shouldRejectReferenceOutsideCurrentContext() {
    WorkingMemory memory = new WorkingMemory(
        2,
        new WorkingMemory.Focus("target-1", 11L, List.of()),
        new WorkingMemory.Deliberation(
            List.of(new WorkingMemory.Hypothesis(
                "候选人可能理解并发冲突",
                "OPEN",
                new WorkingMemory.EvidenceLinks(List.of(99L), List.of())
            )),
            null,
            List.of()
        )
    );

    assertThatThrownBy(() -> validator.validate(memory, references))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Evidence 引用不在当前上下文中");
  }
}
