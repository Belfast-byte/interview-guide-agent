package interview.guide.modules.interview.agent.adaptive.core.event;

/**
 * 候选人提交的回答值对象。
 */
public record CandidateAnswer(
    int turnIndex,
    String content,
    CandidateCodeSubmission codeSubmission
) implements InterviewInputEvent {

  public CandidateAnswer(int turnIndex, String content) {
    this(turnIndex, content, null);
  }
}
