package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntentKey;
import interview.guide.modules.interview.agent.adaptive.core.intent.ActionTarget;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionContext;
import interview.guide.modules.interview.agent.adaptive.core.intent.AskActionPayload;
import interview.guide.modules.interview.agent.adaptive.core.memory.InterviewWorkState;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStateOperation;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatchSource;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptivePlannedAction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

final class ActionIntentPlanFactory {

  private ActionIntentPlanFactory() {}

  static AdaptivePlannedAction ask(
      InterviewWorkState state,
      ActionTarget target,
      AskActionContext context
  ) {
    String intentId = UUID.randomUUID().toString();
    ActionIntent intent = ActionIntent.planned(
        new ActionIntentKey(intentId, state.sessionId(), state.revision()),
        new AskActionPayload(target, intentId, context),
        LocalDateTime.now()
    );
    return new AdaptivePlannedAction(intent, pendingPatch(state, intentId));
  }


  private static WorkStatePatch pendingPatch(
      InterviewWorkState state,
      String intentId
  ) {
    return new WorkStatePatch(
        UUID.randomUUID().toString(),
        state.sessionId(),
        state.revision(),
        state.revision() + 1,
        WorkStatePatchSource.POLICY,
        "intent:" + intentId + ":pending",
        List.of(new WorkStateOperation.SetPendingAction(intentId))
    );
  }
}
