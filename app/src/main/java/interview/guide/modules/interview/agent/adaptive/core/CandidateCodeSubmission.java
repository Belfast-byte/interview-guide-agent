package interview.guide.modules.interview.agent.adaptive.core;

/**
 * 候选人代码提交值对象，用于算法面试沙箱评测。
 */
public record CandidateCodeSubmission(
    String problemId,
    String scenarioId,
    String language,
    String runMode
) {

  public CandidateCodeSubmission(String problemId, String language, String runMode) {
    this(problemId, null, language, runMode);
  }

  public boolean patch() {
    return scenarioId != null;
  }
}
