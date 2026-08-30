package interview.guide.modules.interview.agent.adaptive.core.session;

import java.util.Objects;

/**
 * Turn 触发来源值对象，保证类型和来源引用保持一致。
 */
public record TurnTrigger(
    TurnTriggerType type,
    AssessmentGapSource assessmentGapSource
) {

  public TurnTrigger {
    Objects.requireNonNull(type, "type 不能为空");
    validateSource(type, assessmentGapSource);
  }

  public static TurnTrigger planned() {
    return new TurnTrigger(TurnTriggerType.PLANNED, null);
  }

  public static TurnTrigger assessmentGap(long assessmentId, long probeGapId) {
    return new TurnTrigger(
        TurnTriggerType.ASSESSMENT_GAP,
        new AssessmentGapSource(assessmentId, probeGapId)
    );
  }

  public static TurnTrigger agentDecision() {
    return new TurnTrigger(TurnTriggerType.AGENT_DECISION, null);
  }

  private static void validateSource(
      TurnTriggerType type,
      AssessmentGapSource assessmentGapSource
  ) {
    boolean valid = switch (type) {
      case PLANNED, AGENT_DECISION -> assessmentGapSource == null;
      case ASSESSMENT_GAP -> assessmentGapSource != null;
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
