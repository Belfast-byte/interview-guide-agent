package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.Map;

public record AssessmentEvidenceFacts(
    Map<String, Long> toolCallIdsByResultId
) {

  public AssessmentEvidenceFacts {
    toolCallIdsByResultId = Map.copyOf(toolCallIdsByResultId);
  }
}
