package interview.guide.modules.interview.agent.adaptive.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TurnTriggerTest {

  @Test
  @DisplayName("触发类型各自携带唯一合法来源")
  void shouldCreateValidTriggers() {
    assertThat(TurnTrigger.planned())
        .isEqualTo(new TurnTrigger(TurnTriggerType.PLANNED, null));
    TurnTrigger assessmentGap = TurnTrigger.assessmentGap(7, 8);
    assertThat(assessmentGap.sourceAssessmentId()).isEqualTo(7);
    assertThat(assessmentGap.sourceProbeGapId()).isEqualTo(8);
  }

  @Test
  @DisplayName("计划问题禁止携带来源")
  void shouldRejectSourceForPlannedTrigger() {
    TurnTrigger.AssessmentGapSource source = new TurnTrigger.AssessmentGapSource(1, 2);
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.PLANNED, source))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("评估追问必须同时携带有效 assessment 与 probe gap 来源")
  void shouldRequireAssessmentSource() {
    assertThatThrownBy(() -> new TurnTrigger(TurnTriggerType.ASSESSMENT_GAP, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TurnTrigger.assessmentGap(0, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TurnTrigger.assessmentGap(1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
