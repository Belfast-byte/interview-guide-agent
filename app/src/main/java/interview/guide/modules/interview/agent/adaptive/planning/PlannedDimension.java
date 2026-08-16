package interview.guide.modules.interview.agent.adaptive.planning;

import java.util.List;

/**
 * 已规划维度，包含考察重点、预算和建议技能。
 */
public record PlannedDimension(
    int order,
    String dimension,
    String focus,
    String focusId,
    int suggestedTurns,
    List<String> suggestedTools,
    String suggestedSkill,
    int allocatedTurns,
    int completedTurns,
    PlanDimensionStatus status
) {

  public PlannedDimension {
    suggestedTools = List.copyOf(suggestedTools);
  }

  PlannedDimension answer() {
    int nextCompletedTurns = completedTurns + 1;
    PlanDimensionStatus nextStatus = nextCompletedTurns == allocatedTurns
        ? PlanDimensionStatus.COMPLETED
        : PlanDimensionStatus.IN_PROGRESS;
    return new PlannedDimension(
        order,
        dimension,
        focus,
        focusId,
        suggestedTurns,
        suggestedTools,
        suggestedSkill,
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
        focusId,
        suggestedTurns,
        suggestedTools,
        suggestedSkill,
        allocatedTurns,
        completedTurns,
        PlanDimensionStatus.IN_PROGRESS
    );
  }
}
