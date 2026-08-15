package interview.guide.modules.interview.agent.adaptive.assessment;

import java.util.Map;

public record AssessmentEvidenceFacts(
    Map<String, Long> toolCallIdsByResultId,
    Map<String, String> sandboxExecutionIdsByResultId
) {

  public AssessmentEvidenceFacts(Map<String, Long> toolCallIdsByResultId) {
    this(toolCallIdsByResultId, Map.of());
  }

  public AssessmentEvidenceFacts {
    toolCallIdsByResultId = Map.copyOf(toolCallIdsByResultId);
    sandboxExecutionIdsByResultId = Map.copyOf(sandboxExecutionIdsByResultId);
  }
}
