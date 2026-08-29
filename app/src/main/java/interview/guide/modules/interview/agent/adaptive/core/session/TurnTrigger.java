package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.Objects;

/**
 * Turn 触发来源值对象，保证类型和来源引用保持一致。
 */
public record TurnTrigger(
    TurnTriggerType type,
    AssessmentGapSource assessmentGapSource,
    Long sourceToolResultEventId
) {

  public TurnTrigger {
    Objects.requireNonNull(type, "type 不能为空");
    validateSource(type, assessmentGapSource, sourceToolResultEventId);
  }

  public static TurnTrigger planned() {
    return new TurnTrigger(TurnTriggerType.PLANNED, null, null);
  }

  public static TurnTrigger assessmentGap(long assessmentId, long probeGapId) {
    return new TurnTrigger(
        TurnTriggerType.ASSESSMENT_GAP,
        new AssessmentGapSource(assessmentId, probeGapId),
        null
    );
  }

  public static TurnTrigger agentDecision() {
    return new TurnTrigger(TurnTriggerType.AGENT_DECISION, null, null);
  }

  public static TurnTrigger toolResult(long toolResultEventId) {
    return new TurnTrigger(TurnTriggerType.TOOL_RESULT, null, toolResultEventId);
  }

  private static void validateSource(
      TurnTriggerType type,
      AssessmentGapSource assessmentGapSource,
      Long toolResultEventId
  ) {
    boolean assessmentSource = assessmentGapSource != null;
    boolean toolSource = toolResultEventId != null && toolResultEventId > 0;
    boolean valid = switch (type) {
      case PLANNED, AGENT_DECISION -> !assessmentSource && !toolSource;
      case ASSESSMENT_GAP -> assessmentSource && !toolSource;
      case TOOL_RESULT -> !assessmentSource && toolSource;
    };
    if (!valid) {
      throw new IllegalArgumentException("Turn trigger 与来源引用不匹配");
    }
  }

  public Long sourceAssessmentId() {
    return assessmentGapSource == null ? null : assessmentGapSource.assessmentId();
  }

  public Long sourceProbeGapId() {
    return assessmentGapSource == null ? null : assessmentGapSource.probeGapId();
  }

  /** ASSESSMENT_GAP 的不可拆分来源。 */
  public record AssessmentGapSource(long assessmentId, long probeGapId) {

    public AssessmentGapSource {
      if (assessmentId < 1 || probeGapId < 1) {
        throw new IllegalArgumentException("Assessment 与 ProbeGap ID 必须为正数");
      }
    }
  }
}
