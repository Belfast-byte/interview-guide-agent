package interview.guide.modules.interview.agent.runtime;

/**
 * Agent 面试中的单轮记录，包含问题、回答与评估信息。
 */
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
