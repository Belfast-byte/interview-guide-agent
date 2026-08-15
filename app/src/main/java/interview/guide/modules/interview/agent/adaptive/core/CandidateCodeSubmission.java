package interview.guide.modules.interview.agent.adaptive.core;

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
