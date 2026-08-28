package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.intent.ActionIntent;
import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;

public record AdaptivePlannedAction(
    ActionIntent intent,
    WorkStatePatch pendingPatch
) {}
