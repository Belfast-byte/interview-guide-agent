package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Turn 内的代码任务列；原任务只在首次出题时保存。 */
@Embeddable
public class CodeRepairTurnFields {
  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false)
  private QuestionType questionType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "code_task_json")
  private CodeRepairTask codeTask;

  @Column(name = "code_task_turn_index")
  private Integer codeTaskTurnIndex;

  @Column(name = "submitted_code", columnDefinition = "TEXT")
  private String submittedCode;

  protected CodeRepairTurnFields() {}

  CodeRepairTurnFields(RespondAction question, int turnIndex) {
    validate(question, turnIndex);
    questionType = question.questionType();
    codeTask = question.codeTask();
    codeTaskTurnIndex = codeTask == null ? question.codeTaskTurnIndex() : Integer.valueOf(turnIndex);
  }

  private static void validate(RespondAction question, int turnIndex) {
    if (question.questionType() == null) throw new IllegalArgumentException("questionType 必填");
    if (question.codeTask() != null) {
      if (question.questionType() != QuestionType.CODE_REPAIR || question.codeTaskTurnIndex() != null) {
        throw new IllegalArgumentException("原始代码任务字段组合错误");
      }
      question.codeTask().validate();
      return;
    }
    Integer root = question.codeTaskTurnIndex();
    if (question.questionType() == QuestionType.CODE_REPAIR && root == null) {
      throw new IllegalArgumentException("代码题必须关联原始任务");
    }
    if (root != null && (root < 1 || root >= turnIndex)) {
      throw new IllegalArgumentException("任务引用必须早于当前轮次");
    }
  }

  void recordAnswer(interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer answer) {
    validateAnswer(answer);
    submittedCode = answer.codeRepair() == null ? null : answer.codeRepair().code();
  }

  void validateAnswer(interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer answer) {
    if (questionType == QuestionType.TEXT) {
      if (answer.codeRepair() != null || answer.content() == null || answer.content().isBlank()) {
        throw invalidAnswer("文字题必须提交文字回答且不能提交改错代码");
      }
      return;
    }
    if (answer.codeSubmission() != null || answer.codeRepair() == null
        || answer.codeRepair().code() == null || answer.codeRepair().code().isBlank()) {
      throw invalidAnswer("代码改错题必须提交非空代码且不能进入旧执行协议");
    }
  }

  private static interview.guide.common.exception.BusinessException invalidAnswer(String message) {
    return new interview.guide.common.exception.BusinessException(
        interview.guide.common.exception.ErrorCode.BAD_REQUEST, message);
  }

  public QuestionType questionType() { return questionType; }
  public CodeRepairTask codeTask() { return codeTask; }
  public Integer codeTaskTurnIndex() { return codeTaskTurnIndex; }
  public String submittedCode() { return submittedCode; }
}
