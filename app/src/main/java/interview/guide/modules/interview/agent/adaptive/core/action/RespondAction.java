package interview.guide.modules.interview.agent.adaptive.core.action;

import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;

/**
 * Agent 回复动作，表示直接向候选人输出文本响应。
 */
public record RespondAction(
    AgentResponseType type,
    String content,
    String reason,
    QuestionProvenance questionProvenance,
    CodeQuestionProvenance codeProvenance,
    interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType questionType,
    interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask codeTask,
    Integer codeTaskTurnIndex
) {

  public RespondAction withCodeTask(QuestionType questionType,
      interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask task, Integer root) {
    return new RespondAction(type, content, reason, questionProvenance, codeProvenance,
        questionType, task, root);
  }

  public static RespondAction ask(String question, String reason) {
    return new RespondAction(AgentResponseType.ASK, question, reason, null, null, QuestionType.TEXT, null, null);
  }

  public static RespondAction ask(
      String question,
      String reason,
      QuestionProvenance provenance
  ) {
    return new RespondAction(AgentResponseType.ASK, question, reason, provenance, null, QuestionType.TEXT, null, null);
  }

  public static RespondAction askFromCode(
      String question,
      String reason,
      CodeQuestionProvenance provenance
  ) {
    return new RespondAction(AgentResponseType.ASK, question, reason, null, provenance, QuestionType.TEXT, null, null);
  }

  public static RespondAction finish(String message, String reason) {
    return new RespondAction(AgentResponseType.FINISH, message, reason, null, null, QuestionType.TEXT, null, null);
  }
}
