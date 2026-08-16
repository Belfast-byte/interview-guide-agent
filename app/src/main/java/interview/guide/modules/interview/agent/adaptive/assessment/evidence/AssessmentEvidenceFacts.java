package interview.guide.modules.interview.agent.adaptive.assessment.evidence;

import java.util.Map;

/**
 * 评估证据事实集合。
 */
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
