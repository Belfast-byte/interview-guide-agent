package interview.guide.modules.interview.agent.adaptive.core;

public record CandidateAnswer(
    int turnIndex,
    String content,
    CandidateCodeSubmission codeSubmission
) implements InterviewInputEvent {

  public CandidateAnswer(int turnIndex, String content) {
    this(turnIndex, content, null);
  }
}
