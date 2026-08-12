package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import java.util.List;

public record ReActRequest(
    String sessionId,
    String jd,
    String resume,
    List<AdaptiveInterviewTurn> turns,
    CandidateAnswer candidateAnswer
) {

  public ReActRequest {
    turns = List.copyOf(turns);
  }
}
