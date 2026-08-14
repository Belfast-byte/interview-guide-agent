package interview.guide.modules.interview.agent.adaptive.core;

public record RespondAction(
    AgentResponseType type,
    String content,
    String reason,
    QuestionProvenance questionProvenance
) implements AgentAction {

  public static RespondAction ask(String question, String reason) {
    return new RespondAction(AgentResponseType.ASK, question, reason, null);
  }

  public static RespondAction ask(
      String question,
      String reason,
      QuestionProvenance provenance
  ) {
    return new RespondAction(AgentResponseType.ASK, question, reason, provenance);
  }

  public static RespondAction finish(String message, String reason) {
    return new RespondAction(AgentResponseType.FINISH, message, reason, null);
  }
}
