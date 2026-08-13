package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;

public record PlanningRequest(
    String sessionId,
    PlannerContext context
) {}
