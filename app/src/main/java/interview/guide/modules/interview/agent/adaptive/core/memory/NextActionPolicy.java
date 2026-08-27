package interview.guide.modules.interview.agent.adaptive.core.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import java.util.Optional;

/** 只读取完整 WorkState 的固定顺序下一动作策略。 */
public final class NextActionPolicy {

  private NextActionPolicy() {}

  public static NextAction decide(InterviewWorkState state) {
    if (state.phase() == WorkPhase.AWAITING_ANSWER) {
      return NextAction.simple(NextActionType.WAIT, state.activeTargetId());
    }
    if (state.phase() == WorkPhase.ACTION_PENDING) {
      return NextAction.simple(NextActionType.RESUME_INTENT, state.activeTargetId());
    }
    if (state.phase() == WorkPhase.FINISHED) {
      return NextAction.simple(NextActionType.FINISH, state.activeTargetId());
    }
    TargetWorkState target = state.activeTarget();
    if (reachedExpectedDepth(target) && state.activeOpenIssues().isEmpty()) {
      return terminal(state, TargetWorkStatus.COMPLETED);
    }
    if (reachedDepthCeiling(target) || target.remainingBudget().followUps() == 0) {
      return terminal(state, TargetWorkStatus.EXHAUSTED);
    }
    Optional<WorkIssue> answerIssue = issue(state, CapabilityTarget.EvidenceMethod.CANDIDATE_ANSWER);
    if (answerIssue.isPresent() && target.remainingBudget().turns() > 0) {
      return issueAction(NextActionType.ASK, target, answerIssue.orElseThrow());
    }
    Optional<WorkIssue> toolIssue = issue(state, CapabilityTarget.EvidenceMethod.TOOL_FACT);
    if (toolIssue.isPresent()
        && target.remainingBudget().tools() > 0
        && target.remainingBudget().turns() > 0) {
      return issueAction(NextActionType.CALL_TOOL, target, toolIssue.orElseThrow());
    }
    if (target.remainingBudget().turns() > 0) {
      return NextAction.simple(NextActionType.ASK, target.targetId());
    }
    return terminal(state, TargetWorkStatus.EXHAUSTED);
  }

  private static boolean reachedExpectedDepth(TargetWorkState target) {
    return target.currentDepth().ordinal() >= target.target().depth().expected().ordinal();
  }

  private static boolean reachedDepthCeiling(TargetWorkState target) {
    return target.currentDepth().ordinal() >= target.target().depth().ceiling().ordinal();
  }

  private static Optional<WorkIssue> issue(
      InterviewWorkState state,
      CapabilityTarget.EvidenceMethod method
  ) {
    return state.activeOpenIssues().stream()
        .filter(issue -> issue.evidenceMethod() == method)
        .findFirst();
  }

  private static NextAction issueAction(
      NextActionType type,
      TargetWorkState target,
      WorkIssue issue
  ) {
    return new NextAction(type, target.targetId(), issue.issueId(), null, null);
  }

  private static NextAction terminal(
      InterviewWorkState state,
      TargetWorkStatus status
  ) {
    String nextTargetId = state.nextPendingTargetId();
    if (nextTargetId == null) {
      return new NextAction(
          NextActionType.FINISH,
          state.activeTargetId(),
          null,
          null,
          status
      );
    }
    return new NextAction(
        NextActionType.SWITCH_TARGET,
        state.activeTargetId(),
        null,
        nextTargetId,
        status
    );
  }
}
