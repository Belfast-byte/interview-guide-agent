package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** 已规划维度及其运行进度。 */
public record PlannedDimension(
    CapabilityTarget target,
    int completedTurns,
    PlanDimensionStatus status
) {

  PlannedDimension answer() {
    int nextCompletedTurns = completedTurns + 1;
    PlanDimensionStatus nextStatus = nextCompletedTurns == allocatedTurns()
        ? PlanDimensionStatus.COMPLETED
        : PlanDimensionStatus.IN_PROGRESS;
    return new PlannedDimension(target, nextCompletedTurns, nextStatus);
  }

  PlannedDimension start() {
    return new PlannedDimension(target, completedTurns, PlanDimensionStatus.IN_PROGRESS);
  }

  PlannedDimension withAllocatedTurns(int nextAllocatedTurns) {
    return new PlannedDimension(target.withTurnBudget(nextAllocatedTurns), completedTurns, status);
  }

  public int order() {
    return target.identity().order();
  }

  public String dimension() {
    return target.identity().dimension();
  }

  public String focus() {
    return target.identity().focus();
  }

  public TopicKey topic() {
    return target.identity().topic();
  }

  public String focusId() {
    return topic().focusId();
  }

  public String suggestedSkill() {
    return topic().skillId();
  }

  public int suggestedTurns() {
    return target.budget().suggestedTurns();
  }

  public int allocatedTurns() {
    return target.budget().turnBudget();
  }

  public int followUpBudget() {
    return target.budget().followUpBudget();
  }

  public int toolBudget() {
    return target.budget().toolBudget();
  }

  public DepthLevel expectedDepth() {
    return target.depth().expected();
  }

  public DepthLevel depthCeiling() {
    return target.depth().ceiling();
  }

  public List<CapabilityTarget.EvidenceObjective> evidenceObjectives() {
    return target.evidenceObjectives();
  }

  public List<String> suggestedTools() {
    return target.suggestedTools();
  }
}
