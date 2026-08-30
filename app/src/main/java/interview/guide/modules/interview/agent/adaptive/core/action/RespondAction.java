package interview.guide.modules.interview.agent.adaptive.core.action;

/**
 * Agent 回复动作，表示直接向候选人输出文本响应。
 */
public record RespondAction(
    AgentResponseType type,
    String content,
    String reason,
    QuestionProvenance questionProvenance,
    CodeQuestionProvenance codeProvenance
) implements AgentAction {

  public static RespondAction ask(String question, String reason) {
    return new RespondAction(AgentResponseType.ASK, question, reason, null, null);
  }

  public static RespondAction ask(
      String question,
      String reason,
      QuestionProvenance provenance
  ) {
    return new RespondAction(AgentResponseType.ASK, question, reason, provenance, null);
  }

  public static RespondAction askFromCode(
      String question,
      String reason,
      CodeQuestionProvenance provenance
  ) {
    return new RespondAction(AgentResponseType.ASK, question, reason, null, provenance);
  }

  public static RespondAction finish(String message, String reason) {
    return new RespondAction(AgentResponseType.FINISH, message, reason, null, null);
  }
}
