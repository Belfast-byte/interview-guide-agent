package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextAction;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionPolicy;
import interview.guide.modules.interview.agent.adaptive.core.memory.NextActionType;
import interview.guide.modules.interview.agent.adaptive.core.memory.TargetWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkBudgetType;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateReducer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 把确定性策略决策投影为独立 POLICY Patch。 */
final class WorkStatePolicyPlanner {

  private WorkStatePolicyPlanner() {}

  static PolicyDecision decide(InterviewWorkState state, String sourcePrefix) {
    List<WorkStatePatch> patches = new ArrayList<>();
    InterviewWorkState projected = state;
    NextAction action = NextActionPolicy.decide(projected);
    int step = 1;
    while (action.type() == NextActionType.SWITCH_TARGET) {
      WorkStatePatch patch = patch(
          projected,
          sourcePrefix + ":step:" + step++,
          List.of(new WorkStateOperation.SwitchTarget(
              action.nextTargetId(), action.terminalStatus()))
      );
      patches.add(patch);
      projected = WorkStateReducer.apply(projected, patch);
      action = NextActionPolicy.decide(projected);
    }
    WorkStatePatch finalPatch = patch(
        projected,
        sourcePrefix + ":step:" + step,
        operations(projected.activeTarget(), action)
    );
    patches.add(finalPatch);
    return new PolicyDecision(
        WorkStateReducer.apply(projected, finalPatch),
        action,
        List.copyOf(patches)
    );
  }

  private static List<WorkStateOperation> operations(
      TargetWorkState target,
      NextAction action
  ) {
    return switch (action.type()) {
      case FINISH -> List.of(new WorkStateOperation.FinishSession(action.terminalStatus()));
      case ASK -> askBudget(target, action);
      case CALL_TOOL -> List.of(new WorkStateOperation.ConsumeBudget(
          target.targetId(), WorkBudgetType.TOOL));
      default -> throw new IllegalStateException("当前 WorkState 不能产生可执行策略");
    };
  }

  private static List<WorkStateOperation> askBudget(
      TargetWorkState target,
      NextAction action
  ) {
    List<WorkStateOperation> operations = new ArrayList<>();
    operations.add(new WorkStateOperation.ConsumeBudget(
        target.targetId(), WorkBudgetType.TURN));
    if (action.issueId() != null) {
      operations.add(new WorkStateOperation.ConsumeBudget(
          target.targetId(), WorkBudgetType.FOLLOW_UP));
    }
    return List.copyOf(operations);
  }

  private static WorkStatePatch patch(
      InterviewWorkState state,
      String sourceId,
      List<WorkStateOperation> operations
  ) {
    return new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.POLICY,
        sourceId,
        operations
    );
  }

  record PolicyDecision(
      InterviewWorkState state,
      NextAction action,
      List<WorkStatePatch> patches
  ) {}
}
