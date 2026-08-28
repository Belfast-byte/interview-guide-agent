package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveAgentTurnEntityTest {

  @Test
  @DisplayName("实体和领域对象完整保留评估追问来源")
  void shouldMapAssessmentGapProvenance() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        "session-1",
        2,
        0,
        RespondAction.ask("追问", "评估 gap"),
        TurnProvenance.assessmentGap(1, 42, 84)
    ));

    assertThat(turn.parentTurnIndex()).isEqualTo(1);
    assertThat(turn.triggerType()).isEqualTo(TurnTriggerType.ASSESSMENT_GAP);
    assertThat(turn.sourceAssessmentId()).isEqualTo(42);
    assertThat(turn.sourceProbeGapId()).isEqualTo(84);
    assertThat(turn.toDomain().provenance())
        .isEqualTo(TurnProvenance.assessmentGap(1, 42, 84));
  }

  @Test
  @DisplayName("父轮次不早于当前轮次时拒绝创建")
  void shouldRejectInvalidParentTurn() {
    assertThatThrownBy(() -> new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        "session-1",
        2,
        0,
        RespondAction.ask("追问", "非法父链"),
        TurnProvenance.assessmentGap(2, 42, 84)
    ))).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("父轮次必须早于");
  }
}
