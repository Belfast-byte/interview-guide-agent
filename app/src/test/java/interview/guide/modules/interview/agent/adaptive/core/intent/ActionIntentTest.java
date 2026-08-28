package interview.guide.modules.interview.agent.adaptive.core.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionIntentTest {

  private static final LocalDateTime PLANNED_AT = LocalDateTime.of(2026, 8, 28, 6, 0);

  @Test
  @DisplayName("ActionIntent 只允许 planned executing succeeded applied 顺序迁移")
  void shouldFollowSuccessfulLifecycle() {
    ActionIntent planned = planned();
    ActionIntent executing = planned.start(PLANNED_AT.plusSeconds(1));
    ActionIntent succeeded = executing.succeed(
        ActionIntentOutcome.succeeded(ActionResultType.QUESTION, "turn:2"),
        PLANNED_AT.plusSeconds(2)
    );
    ActionIntent applied = succeeded.apply(PLANNED_AT.plusSeconds(3));

    assertThat(executing.progress().status()).isEqualTo(ActionIntentStatus.EXECUTING);
    assertThat(succeeded.progress().outcome().resultRef()).isEqualTo("turn:2");
    assertThat(applied.progress().status()).isEqualTo(ActionIntentStatus.APPLIED);
    assertThat(applied.progress().timing().createdAt()).isEqualTo(PLANNED_AT);
  }

  @Test
  @DisplayName("非法跳转和不完整成功结果明确失败")
  void shouldRejectIllegalTransitions() {
    assertThatThrownBy(() -> planned().apply(PLANNED_AT.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> planned().start(PLANNED_AT.plusSeconds(1)).succeed(
        ActionIntentOutcome.none(), PLANNED_AT.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("结果不完整");
  }

  @Test
  @DisplayName("失败保留现场且不能再隐式执行")
  void shouldKeepFailureTerminal() {
    ActionIntent failed = planned().start(PLANNED_AT.plusSeconds(1))
        .fail("模型调用失败", PLANNED_AT.plusSeconds(2));

    assertThat(failed.progress().status()).isEqualTo(ActionIntentStatus.FAILED);
    assertThat(failed.progress().outcome().error()).isEqualTo("模型调用失败");
    assertThatThrownBy(() -> failed.restart(PLANNED_AT.plusSeconds(3)))
        .isInstanceOf(IllegalStateException.class);
  }

  private ActionIntent planned() {
    return ActionIntent.planned(
        new ActionIntentKey("intent-1", "session-1", 2),
        new AskActionPayload(
            new ActionTarget("target-0", "issue-1", 2),
            "key-1",
            new AskActionContext(NextTurnProvenanceDraft.planned(), null)
        ),
        PLANNED_AT
    );
  }
}
