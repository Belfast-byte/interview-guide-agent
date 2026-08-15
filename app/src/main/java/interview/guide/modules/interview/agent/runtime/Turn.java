package interview.guide.modules.interview.agent.runtime;

public record Turn(
    int turnNumber,
    String question,
    String answer,
    AnswerDepthLevel assessment,
    AnswerEvidence evidence
) {

  public Turn(int turnNumber, String question, String answer) {
    this(turnNumber, question, answer, null, null);
  }
}
