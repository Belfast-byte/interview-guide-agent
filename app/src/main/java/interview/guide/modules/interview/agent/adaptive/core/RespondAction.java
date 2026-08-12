package interview.guide.modules.interview.agent.adaptive.core;

public record RespondAction(
    AgentResponseType type,
    String content,
    String reason
) implements AgentAction {

  public static RespondAction ask(String question, String reason) {
    return new RespondAction(AgentResponseType.ASK, question, reason);
  }

  public static RespondAction finish(String message, String reason) {
    return new RespondAction(AgentResponseType.FINISH, message, reason);
  }
}
