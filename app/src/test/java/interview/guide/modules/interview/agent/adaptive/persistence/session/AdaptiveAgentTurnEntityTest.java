package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveAgentTurnEntityTest {

  @Test
  @DisplayName("替换问题时清除旧问题的题库和代码来源")
  void shouldClearPreviousProvenanceWhenReplacingQuestion() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(new AdaptiveTurnCreation(
        "session-1",
        2,
        0,
        new RespondAction(
            RespondAction.ask("原问题", "题库与代码来源").type(),
            "原问题",
            "题库与代码来源",
            new QuestionProvenance("question:42", "MEDIUM"),
            new CodeQuestionProvenance(
                "scenario-1",
                "OrderCache.java:42",
                CodeFactUsage.QUESTION_SOURCE
            )
        ),
        TurnProvenance.initial()
    ));

    turn.replaceQuestion(
        RespondAction.ask("新追问", "工具结果驱动"),
        TurnProvenance.toolResult(1, 12)
    );

    assertThat(turn.question()).isEqualTo("新追问");
    assertThat(turn.questionSourceId()).isNull();
    assertThat(turn.questionDifficulty()).isNull();
    assertThat(turn.codeSourceId()).isNull();
    assertThat(turn.codeAnchor()).isNull();
    assertThat(turn.codeFactUsage()).isNull();
    assertThat(turn.triggerType()).isEqualTo(TurnTriggerType.TOOL_RESULT);
    assertThat(turn.sourceToolResultEventId()).isEqualTo(12);
  }

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
