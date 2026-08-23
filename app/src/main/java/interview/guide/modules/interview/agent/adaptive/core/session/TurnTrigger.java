package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.Objects;

/**
 * Turn 触发来源值对象，保证类型和来源引用保持一致。
 */
public record TurnTrigger(
    TurnTriggerType type,
    Long sourceAssessmentId,
    Long sourceToolResultEventId
) {

  public TurnTrigger {
    Objects.requireNonNull(type, "type 不能为空");
    validateSource(type, sourceAssessmentId, sourceToolResultEventId);
  }

  public static TurnTrigger planned() {
    return new TurnTrigger(TurnTriggerType.PLANNED, null, null);
  }

  public static TurnTrigger assessmentGap(long assessmentId) {
    return new TurnTrigger(TurnTriggerType.ASSESSMENT_GAP, assessmentId, null);
  }

  public static TurnTrigger toolResult(long toolResultEventId) {
    return new TurnTrigger(TurnTriggerType.TOOL_RESULT, null, toolResultEventId);
  }

  private static void validateSource(
      TurnTriggerType type,
      Long assessmentId,
      Long toolResultEventId
  ) {
    boolean assessmentSource = assessmentId != null && assessmentId > 0;
    boolean toolSource = toolResultEventId != null && toolResultEventId > 0;
    boolean valid = switch (type) {
      case PLANNED -> !assessmentSource && !toolSource;
      case ASSESSMENT_GAP -> assessmentSource && !toolSource;
      case TOOL_RESULT -> !assessmentSource && toolSource;
    };
    if (!valid) {
      throw new IllegalArgumentException("Turn trigger 与来源引用不匹配");
    }
  }
}
