package interview.guide.modules.interview.agent.adaptive.core.event;

/**
 * 候选人提交的回答值对象。
 */
public record CandidateAnswer(
    int turnIndex,
    String content,
    CandidateCodeSubmission codeSubmission,
    CodeRepairAnswer codeRepair
) {

  public record CodeRepairAnswer(String code) {}

  public CandidateAnswer {
    content = content == null || content.isBlank() ? null : content;
  }

  public CandidateAnswer(int turnIndex, String content, CandidateCodeSubmission codeSubmission) {
    this(turnIndex, content, codeSubmission, null);
  }

  public CandidateAnswer(int turnIndex, String content) {
    this(turnIndex, content, null);
  }
}
