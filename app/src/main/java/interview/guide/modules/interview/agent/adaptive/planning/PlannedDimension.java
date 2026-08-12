package interview.guide.modules.interview.agent.adaptive.planning;

public record PlannedDimension(
    int order,
    String dimension,
    String focus,
    int suggestedTurns,
    int allocatedTurns,
    int completedTurns,
    PlanDimensionStatus status
) {

  PlannedDimension answer() {
    int nextCompletedTurns = completedTurns + 1;
    PlanDimensionStatus nextStatus = nextCompletedTurns == allocatedTurns
        ? PlanDimensionStatus.COMPLETED
        : PlanDimensionStatus.IN_PROGRESS;
    return new PlannedDimension(
        order,
        dimension,
        focus,
        suggestedTurns,
        allocatedTurns,
        nextCompletedTurns,
        nextStatus
    );
  }

  PlannedDimension start() {
    return new PlannedDimension(
        order,
        dimension,
        focus,
        suggestedTurns,
        allocatedTurns,
        completedTurns,
        PlanDimensionStatus.IN_PROGRESS
    );
  }
}
