package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.PlannerContext;

/**
 * 规划请求，包含会话 ID 和规划上下文。
 */
public record PlanningRequest(
    String sessionId,
    PlannerContext context,
    ProjectPlanningContext project
) {

  public PlanningRequest(String sessionId, PlannerContext context) {
    this(sessionId, context, null);
  }
}
