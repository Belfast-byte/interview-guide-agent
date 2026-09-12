package interview.guide.modules.interview.agent.adaptive.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeQuestionValidatorTest {
  @Test
  void acceptsNewTaskAndExplicitText() {
    assertThatCode(() -> CodeQuestionValidator.validateShape(question(QuestionType.CODE_REPAIR, task(), null)))
        .doesNotThrowAnyException();
    assertThatCode(() -> CodeQuestionValidator.validateShape(question(QuestionType.TEXT, null, null)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAmbiguousOrMissingTaskWithoutDefaultingToText() {
    assertThatThrownBy(() -> CodeQuestionValidator.validateShape(question(null, null, null)))
        .hasMessageContaining("questionType");
    assertThatThrownBy(() -> CodeQuestionValidator.validateShape(question(QuestionType.TEXT, task(), null)))
        .hasMessageContaining("新代码任务");
    assertThatThrownBy(() -> CodeQuestionValidator.validateShape(question(QuestionType.CODE_REPAIR, null, null)))
        .hasMessageContaining("原始任务引用");
    assertThatThrownBy(() -> CodeQuestionValidator.validateShape(question(QuestionType.CODE_REPAIR, task(), 1)))
        .hasMessageContaining("新代码任务");
  }

  @Test
  void rejectsDuplicateReviewChecks() {
    var check = task().reviewGuide().checks().getFirst();
    var invalid = new CodeRepairTask("code", List.of("要求"), List.of("假设"),
        new CodeRepairTask.ReviewGuide(List.of(check, check)));
    assertThatThrownBy(invalid::validate).hasMessageContaining("ID 重复");
  }

  private AgentDecision.QuestionDraft question(QuestionType type, CodeRepairTask task, Integer root) {
    return new AgentDecision.QuestionDraft("修复库存预留", "验证并发", List.of(), type, task, root);
  }

  private CodeRepairTask task() {
    return new CodeRepairTask("void reserve() {}", List.of("不能超卖"), List.of("多实例共享数据库"),
        new CodeRepairTask.ReviewGuide(List.of(new CodeRepairTask.Check(
            "C1", "检查与扣减分离", "并发预留", "条件扣减保持原子性"))));
  }
}
