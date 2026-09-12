package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;

/** 模型题目字段组合校验；任务归属在提交事务中再次核对。 */
public final class CodeQuestionValidator {
  private CodeQuestionValidator() {}

  public static void validateShape(AgentDecision.QuestionDraft question) {
    if (question.questionType() == null) throw new IllegalArgumentException("questionType 必填");
    if (question.codeTask() != null) {
      if (question.questionType() != QuestionType.CODE_REPAIR || question.codeTaskTurnIndex() != null) {
        throw new IllegalArgumentException("新代码任务必须为 CODE_REPAIR 且不能同时引用旧任务");
      }
      question.codeTask().validate();
      return;
    }
    if (question.questionType() == QuestionType.CODE_REPAIR && question.codeTaskTurnIndex() == null) {
      throw new IllegalArgumentException("CODE_REPAIR 必须提供新任务或原始任务引用");
    }
    if (question.codeTaskTurnIndex() != null && question.codeTaskTurnIndex() < 1) {
      throw new IllegalArgumentException("原始任务轮次必须为正数");
    }
  }

  public static void validate(AgentDecision.QuestionDraft question, AgentContext context) {
    validateShape(question);
    if (question.codeTaskTurnIndex() == null) return;
    var root = context.facts().recentTurns().stream()
        .filter(turn -> turn.turnIndex() == question.codeTaskTurnIndex()).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("任务引用不属于本场历史"));
    if (root.codeTask() == null || !java.util.Objects.equals(root.codeTaskTurnIndex(), root.turnIndex())) {
      throw new IllegalArgumentException("任务引用必须指向原始任务，不能引用另一条引用");
    }
    if (question.questionType() == QuestionType.CODE_REPAIR
        && context.session().mode() != SessionMode.PRACTICE) {
      throw new IllegalArgumentException("评估模式只能对已提交代码进行文字追问");
    }
  }
}
