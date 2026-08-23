package interview.guide.modules.interview.agent.adaptive.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TurnTriggerTest {

  @Test
  @DisplayName("三种触发类型各自携带唯一合法来源")
  void shouldCreateValidTriggers() {
    assertThat(TurnTrigger.planned())
        .isEqualTo(new TurnTrigger(TurnTriggerType.PLANNED, null, null));
    TurnTrigger assessmentGap = TurnTrigger.assessmentGap(7, 8);
    assertThat(assessmentGap.sourceAssessmentId()).isEqualTo(7);
    assertThat(assessmentGap.sourceProbeGapId()).isEqualTo(8);
    assertThat(TurnTrigger.toolResult(9).sourceToolResultEventId()).isEqualTo(9);
  }

  @Test
  @DisplayName("计划问题禁止携带来源")
  void shouldRejectSourceForPlannedTrigger() {
    TurnTrigger.AssessmentGapSource source = new TurnTrigger.AssessmentGapSource(1, 2);
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.PLANNED, source, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("评估追问必须同时携带有效 assessment 与 probe gap 来源")
  void shouldRequireAssessmentSource() {
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.ASSESSMENT_GAP, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    TurnTrigger.AssessmentGapSource source = new TurnTrigger.AssessmentGapSource(1, 2);
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.ASSESSMENT_GAP, source, 3L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TurnTrigger.assessmentGap(0, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TurnTrigger.assessmentGap(1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("工具追问必须只携带有效 tool event 来源")
  void shouldRequireToolResultSource() {
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.TOOL_RESULT, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    TurnTrigger.AssessmentGapSource source = new TurnTrigger.AssessmentGapSource(1, 2);
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.TOOL_RESULT, source, 3L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TurnTrigger.toolResult(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
