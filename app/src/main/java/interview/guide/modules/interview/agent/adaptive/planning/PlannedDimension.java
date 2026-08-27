package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.util.List;

/** Planner 裁决后的不可变能力目标。运行进度只保存在 WorkState。 */
public record PlannedDimension(CapabilityTarget target) {

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
