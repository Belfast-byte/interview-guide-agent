package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import java.util.Objects;

/**
 * 已持久化 Assessment 的等级修订事实。
 */
public record AssessmentRevision(
    String sessionId,
    int turnIndex,
    DepthLevel oldLevel,
    DepthLevel newLevel,
    String llmProvider
) {

  public AssessmentRevision {
    Objects.requireNonNull(sessionId, "sessionId 不能为空");
    Objects.requireNonNull(oldLevel, "oldLevel 不能为空");
    Objects.requireNonNull(newLevel, "newLevel 不能为空");
    if (sessionId.isBlank() || turnIndex < 1) {
      throw new IllegalArgumentException("Assessment 修订来源非法");
    }
  }

  public boolean changesLevel() {
    return oldLevel != newLevel;
  }
}
