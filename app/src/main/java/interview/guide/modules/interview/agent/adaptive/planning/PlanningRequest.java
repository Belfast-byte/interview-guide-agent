package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;

public record PlanningRequest(
    String sessionId,
    PlannerContext context,
    ProjectPlanningContext project
) {

  public PlanningRequest(String sessionId, PlannerContext context) {
    this(sessionId, context, null);
  }
}
