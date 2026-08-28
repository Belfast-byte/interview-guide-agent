package interview.guide.modules.interview.agent.adaptive.core.intent;

import java.time.LocalDateTime;

/** 外部动作的可恢复持久意图。 */
public record ActionIntent(
    ActionIntentKey key,
    ActionIntentPayload payload,
    ActionIntentProgress progress
) {

  public static ActionIntent planned(
      ActionIntentKey key,
      ActionIntentPayload payload,
      LocalDateTime now
  ) {
    return new ActionIntent(
        key,
        payload,
        new ActionIntentProgress(
            ActionIntentStatus.PLANNED,
            ActionIntentOutcome.none(),
            new ActionIntentTiming(null, now, now)
        )
    );
  }

  public ActionIntent start(LocalDateTime now) {
    requireStatus(ActionIntentStatus.PLANNED);
    return withProgress(
        ActionIntentStatus.EXECUTING,
        ActionIntentOutcome.none(),
        timing(now, now)
    );
  }

  public ActionIntent restart(LocalDateTime now) {
    requireStatus(ActionIntentStatus.EXECUTING);
    return withProgress(
        ActionIntentStatus.EXECUTING,
        ActionIntentOutcome.none(),
        timing(now, now)
    );
  }

  public ActionIntent succeed(ActionIntentOutcome outcome, LocalDateTime now) {
    requireStatus(ActionIntentStatus.EXECUTING);
    if (outcome.resultType() == null || outcome.resultRef() == null) {
      throw new IllegalStateException("ActionIntent 成功结果不完整");
    }
    return withProgress(ActionIntentStatus.SUCCEEDED, outcome,
        timing(progress.timing().executionStartedAt(), now));
  }

  public ActionIntent apply(LocalDateTime now) {
    requireStatus(ActionIntentStatus.SUCCEEDED);
    return withProgress(ActionIntentStatus.APPLIED, progress.outcome(),
        timing(progress.timing().executionStartedAt(), now));
  }

  public ActionIntent fail(String error, LocalDateTime now) {
    if (progress.status() != ActionIntentStatus.PLANNED
        && progress.status() != ActionIntentStatus.EXECUTING) {
      throw new IllegalStateException("当前 ActionIntent 状态不能失败");
    }
    return withProgress(ActionIntentStatus.FAILED, ActionIntentOutcome.failed(error),
        timing(progress.timing().executionStartedAt(), now));
  }

  private ActionIntent withProgress(
      ActionIntentStatus status,
      ActionIntentOutcome outcome,
      ActionIntentTiming timing
  ) {
    return new ActionIntent(key, payload, new ActionIntentProgress(status, outcome, timing));
  }

  private ActionIntentTiming timing(LocalDateTime startedAt, LocalDateTime updatedAt) {
    return new ActionIntentTiming(startedAt, progress.timing().createdAt(), updatedAt);
  }

  private void requireStatus(ActionIntentStatus expected) {
    if (progress.status() != expected) {
      throw new IllegalStateException("ActionIntent 状态不允许当前迁移");
    }
  }
}
