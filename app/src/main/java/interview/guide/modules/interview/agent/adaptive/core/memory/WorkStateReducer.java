package interview.guide.modules.interview.agent.adaptive.core.memory;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionResultType;
import java.util.ArrayList;
import java.util.List;

/** Typed Patch 的纯 Java 归约器。 */
public final class WorkStateReducer {

  private WorkStateReducer() {}

  public static InterviewWorkState apply(
      InterviewWorkState state,
      WorkStatePatch patch
  ) {
    validatePatch(state, patch);
    InterviewWorkState updated = state;
    for (WorkStateOperation operation : patch.operations()) {
      updated = applyOperation(updated, operation);
    }
    return updated.withRevision(patch.resultRevision());
  }

  private static void validatePatch(InterviewWorkState state, WorkStatePatch patch) {
    if (!state.sessionId().equals(patch.sessionId())) {
      throw new IllegalStateException("Patch 不属于当前会话");
    }
    if (state.revision() != patch.baseRevision()) {
      throw new IllegalStateException("WorkState revision 冲突");
    }
  }

  private static InterviewWorkState applyOperation(
      InterviewWorkState state,
      WorkStateOperation operation
  ) {
    return switch (operation) {
      case WorkStateOperation.AddEvidenceRef add -> addEvidence(state, add.evidenceRef());
      case WorkStateOperation.OpenIssue open -> openIssue(state, open.issue());
      case WorkStateOperation.CloseIssue close -> closeIssue(state, close);
      case WorkStateOperation.UpdateTargetDepth update -> updateDepth(state, update);
      case WorkStateOperation.SetFocus focus -> state.withFocus(focus.attentionFocus());
      case WorkStateOperation.ConsumeBudget consume -> consumeBudget(state, consume);
      default -> applyTransition(state, operation);
    };
  }

  private static InterviewWorkState applyTransition(
      InterviewWorkState state,
      WorkStateOperation operation
  ) {
    return switch (operation) {
      case WorkStateOperation.SwitchTarget change -> switchTarget(state, change);
      case WorkStateOperation.SetPendingAction pending -> setPending(state, pending);
      case WorkStateOperation.RetryPendingAction retry -> retryPending(state, retry);
      case WorkStateOperation.ApplyActionResult result -> applyActionResult(state, result);
      case WorkStateOperation.CompleteAnswer answer -> completeAnswer(state, answer);
      case WorkStateOperation.FinishSession finish -> finish(state, finish.currentStatus());
      default -> throw new IllegalStateException("未知 WorkState operation");
    };
  }

  private static InterviewWorkState addEvidence(
      InterviewWorkState state,
      WorkEvidenceRef evidence
  ) {
    if (!state.activeTargetId().equals(evidence.targetId())) {
      throw new IllegalStateException("证据不属于当前目标");
    }
    List<WorkEvidenceRef> refs = new ArrayList<>(state.activeEvidenceRefs());
    if (!refs.contains(evidence)) {
      refs.add(evidence);
    }
    return state.withEvidence(refs);
  }

  private static InterviewWorkState openIssue(
      InterviewWorkState state,
      WorkIssue issue
  ) {
    if (!state.activeTargetId().equals(issue.targetId())) {
      throw new IllegalStateException("问题不属于当前目标");
    }
    if (state.openIssues().stream().anyMatch(item -> item.issueId().equals(issue.issueId()))) {
      throw new IllegalStateException("问题 ID 已存在");
    }
    List<WorkIssue> issues = new ArrayList<>(state.openIssues());
    issues.add(issue);
    return state.withIssues(issues);
  }

  private static InterviewWorkState closeIssue(
      InterviewWorkState state,
      WorkStateOperation.CloseIssue operation
  ) {
    if (operation.status() != WorkIssueStatus.RESOLVED
        && operation.status() != WorkIssueStatus.ABANDONED) {
      throw new IllegalStateException("问题只能关闭为 RESOLVED 或 ABANDONED");
    }
    boolean found = state.openIssues().stream()
        .anyMatch(issue -> issue.issueId().equals(operation.issueId()) && issue.isOpen());
    if (!found) {
      throw new IllegalStateException("待关闭问题不存在或已经终态");
    }
    return state.withIssues(state.openIssues().stream()
        .map(issue -> issue.issueId().equals(operation.issueId())
            ? issue.close(operation.status(), operation.reason())
            : issue)
        .toList());
  }

  private static InterviewWorkState updateDepth(
      InterviewWorkState state,
      WorkStateOperation.UpdateTargetDepth operation
  ) {
    requireTarget(state, operation.targetId());
    return state.withTargets(state.targets().stream()
        .map(target -> target.targetId().equals(operation.targetId())
            ? target.withDepth(operation.depth())
            : target)
        .toList());
  }

  private static InterviewWorkState consumeBudget(
      InterviewWorkState state,
      WorkStateOperation.ConsumeBudget operation
  ) {
    requireTarget(state, operation.targetId());
    return state.withTargets(state.targets().stream()
        .map(target -> target.targetId().equals(operation.targetId())
            ? target.consume(operation.budgetType())
            : target)
        .toList());
  }

  private static InterviewWorkState switchTarget(
      InterviewWorkState state,
      WorkStateOperation.SwitchTarget operation
  ) {
    requirePhase(state, WorkPhase.READY_TO_DECIDE);
    if (operation.currentStatus() != TargetWorkStatus.COMPLETED
        && operation.currentStatus() != TargetWorkStatus.EXHAUSTED) {
      throw new IllegalStateException("当前目标只能结束为 COMPLETED 或 EXHAUSTED");
    }
    return state.switchTarget(operation.nextTargetId(), operation.currentStatus());
  }

  private static InterviewWorkState setPending(
      InterviewWorkState state,
      WorkStateOperation.SetPendingAction operation
  ) {
    requirePhase(state, WorkPhase.READY_TO_DECIDE);
    return state.withExecution(WorkPhase.ACTION_PENDING, null, null, operation.intentId());
  }

  private static InterviewWorkState applyActionResult(
      InterviewWorkState state,
      WorkStateOperation.ApplyActionResult operation
  ) {
    if (state.phase() != WorkPhase.READY_TO_DECIDE
        && state.phase() != WorkPhase.ACTION_PENDING) {
      throw new IllegalStateException("当前阶段不能应用动作结果");
    }
    if (operation.resultType() == ActionResultType.TOOL_RESULT) {
      return state.withExecution(WorkPhase.READY_TO_DECIDE, null, null, null);
    }
    if (operation.turnIndex() == null) {
      throw new IllegalStateException("问题动作结果缺少轮次");
    }
    return state.withExecution(WorkPhase.AWAITING_ANSWER,
        operation.turnIndex(), operation.issueId(), null);
  }

  private static InterviewWorkState retryPending(
      InterviewWorkState state,
      WorkStateOperation.RetryPendingAction operation
  ) {
    requirePhase(state, WorkPhase.ACTION_PENDING);
    if (!operation.failedIntentId().equals(state.activeActionIntentId())) {
      throw new IllegalStateException("失败 Intent 与 WorkState 不一致");
    }
    return state.withExecution(
        WorkPhase.ACTION_PENDING, null, null, operation.retryIntentId());
  }

  private static InterviewWorkState completeAnswer(
      InterviewWorkState state,
      WorkStateOperation.CompleteAnswer operation
  ) {
    requirePhase(state, WorkPhase.AWAITING_ANSWER);
    if (state.awaitingAnswerTurnIndex() == null
        || operation.turnIndex() != state.awaitingAnswerTurnIndex()) {
      throw new IllegalStateException("回答轮次与 WorkState 不一致");
    }
    return state.withExecution(WorkPhase.READY_TO_DECIDE, null, null, null);
  }

  private static InterviewWorkState finish(
      InterviewWorkState state,
      TargetWorkStatus currentStatus
  ) {
    requirePhase(state, WorkPhase.READY_TO_DECIDE);
    if (currentStatus != TargetWorkStatus.COMPLETED
        && currentStatus != TargetWorkStatus.EXHAUSTED) {
      throw new IllegalStateException("结束会话时当前目标必须进入终态");
    }
    return state.finish(currentStatus);
  }

  private static void requirePhase(InterviewWorkState state, WorkPhase required) {
    if (state.phase() != required) {
      throw new IllegalStateException("WorkState 阶段不允许当前操作");
    }
  }

  private static void requireTarget(InterviewWorkState state, String targetId) {
    boolean exists = state.targets().stream()
        .anyMatch(target -> target.targetId().equals(targetId));
    if (!exists) {
      throw new IllegalStateException("WorkState 目标不存在");
    }
  }
}
