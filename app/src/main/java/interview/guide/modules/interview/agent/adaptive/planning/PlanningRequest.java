package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.PracticeMemoryService.PracticePlanningMemory;

/**
 * 规划请求，包含会话 ID 和规划上下文。
 */
public record PlanningRequest(
    String sessionId,
    PlannerContext context,
    PracticePlanningMemory practiceMemory,
    boolean codeRepairFirst
) {

  public PlanningRequest(String sessionId, PlannerContext context, PracticePlanningMemory practiceMemory) {
    this(sessionId, context, practiceMemory, false);
  }

  public PlanningRequest {
    boolean practice = context.mode() == SessionMode.PRACTICE;
    if (practice != (practiceMemory != null)) {
      throw new IllegalArgumentException("只有练习规划可以携带 Semantic memory");
    }
  }
}
