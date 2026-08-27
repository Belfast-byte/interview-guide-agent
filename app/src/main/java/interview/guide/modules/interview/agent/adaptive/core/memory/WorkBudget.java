package interview.guide.modules.interview.agent.adaptive.core.memory;

/** 当前目标的剩余预算。 */
public record WorkBudget(int turns, int followUps, int tools) {

  public WorkBudget consume(WorkBudgetType type) {
    return switch (type) {
      case TURN -> new WorkBudget(requireAvailable(turns, type) - 1, followUps, tools);
      case FOLLOW_UP -> new WorkBudget(turns, requireAvailable(followUps, type) - 1, tools);
      case TOOL -> new WorkBudget(turns, followUps, requireAvailable(tools, type) - 1);
    };
  }

  private static int requireAvailable(int remaining, WorkBudgetType type) {
    if (remaining < 1) {
      throw new IllegalStateException(type + " 预算已用尽");
    }
    return remaining;
  }
}
