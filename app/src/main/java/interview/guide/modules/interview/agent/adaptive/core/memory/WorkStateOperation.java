package interview.guide.modules.interview.agent.adaptive.core.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;

/** 允许改变 WorkState 的封闭操作集合。 */
public sealed interface WorkStateOperation permits
    WorkStateOperation.AddEvidenceRef,
    WorkStateOperation.OpenIssue,
    WorkStateOperation.CloseIssue,
    WorkStateOperation.UpdateTargetDepth,
    WorkStateOperation.SetFocus,
    WorkStateOperation.ConsumeBudget,
    WorkStateOperation.SwitchTarget,
    WorkStateOperation.SetPendingAction,
    WorkStateOperation.RetryPendingAction,
    WorkStateOperation.ApplyActionResult,
    WorkStateOperation.CompleteAnswer,
    WorkStateOperation.FinishSession {

  record AddEvidenceRef(WorkEvidenceRef evidenceRef) implements WorkStateOperation {}

  record OpenIssue(WorkIssue issue) implements WorkStateOperation {}

  record CloseIssue(
      String issueId,
      WorkIssueStatus status,
      String reason
  ) implements WorkStateOperation {}

  record UpdateTargetDepth(String targetId, DepthLevel depth) implements WorkStateOperation {}

  record SetFocus(String attentionFocus) implements WorkStateOperation {}

  record ConsumeBudget(String targetId, WorkBudgetType budgetType)
      implements WorkStateOperation {}

  record SwitchTarget(
      String nextTargetId,
      TargetWorkStatus currentStatus
  ) implements WorkStateOperation {}

  record SetPendingAction(String intentId) implements WorkStateOperation {}

  record RetryPendingAction(
      String failedIntentId,
      String retryIntentId
  ) implements WorkStateOperation {}

  record ApplyActionResult(
      ActionResultType resultType,
      Integer turnIndex,
      String issueId
  ) implements WorkStateOperation {}

  record CompleteAnswer(int turnIndex) implements WorkStateOperation {}

  record FinishSession(TargetWorkStatus currentStatus) implements WorkStateOperation {}
}
