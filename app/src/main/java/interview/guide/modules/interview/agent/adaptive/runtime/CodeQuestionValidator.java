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

  public static void validate(AgentDecision.Ask ask, AgentContext context) {
    var question = ask.question();
    validateShape(question);
    if (ask.sourceGapId() != null) validateGapSource(ask, context);
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
  private static void validateGapSource(AgentDecision.Ask ask, AgentContext context) {
    var gap = context.facts().coverage().openProbeGaps().stream()
        .filter(item -> item.gapId() == ask.sourceGapId()).findFirst().orElseThrow();
    var source = context.facts().recentTurns().stream()
        .filter(turn -> turn.turnIndex() == gap.sourceTurnIndex()).findFirst().orElseThrow();
    validateGapReference(ask.question(), source.codeTaskTurnIndex());
  }

  /** 追问代码缺口须沿用原任务；显式生成的新代码任务仍可验证同一缺口。 */
  public static void validateGapReference(AgentDecision.QuestionDraft question, Integer sourceRoot) {
    if (question.codeTask() == null && sourceRoot != null
        && !java.util.Objects.equals(question.codeTaskTurnIndex(), sourceRoot)) {
      throw new IllegalArgumentException("代码缺口追问必须引用来源的原始代码任务");
    }
  }
}
