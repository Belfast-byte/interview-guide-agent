package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeFactUsage;
import interview.guide.modules.interview.agent.adaptive.core.context.CodeQuestionProvenance;
import interview.guide.modules.interview.agent.adaptive.core.context.QuestionProvenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdaptiveAgentTurnEntityTest {

  @Test
  @DisplayName("替换问题时清除旧问题的题库和代码来源")
  void shouldClearPreviousProvenanceWhenReplacingQuestion() {
    AdaptiveAgentTurnEntity turn = new AdaptiveAgentTurnEntity(
        "session-1",
        1,
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
        )
    );

    turn.replaceQuestion(RespondAction.ask("新追问", "工具结果驱动"));

    assertThat(turn.question()).isEqualTo("新追问");
    assertThat(turn.questionSourceId()).isNull();
    assertThat(turn.questionDifficulty()).isNull();
    assertThat(turn.codeSourceId()).isNull();
    assertThat(turn.codeAnchor()).isNull();
    assertThat(turn.codeFactUsage()).isNull();
  }
}
