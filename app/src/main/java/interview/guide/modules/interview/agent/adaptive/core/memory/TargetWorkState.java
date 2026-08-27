package interview.guide.modules.interview.agent.adaptive.core.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;

/** 不可变能力目标及其当前进展。 */
public record TargetWorkState(
    String targetId,
    CapabilityTarget target,
    WorkBudget remainingBudget,
    DepthLevel currentDepth,
    TargetWorkStatus status
) {

  public TargetWorkState withDepth(DepthLevel depth) {
    DepthLevel next = depth.ordinal() > currentDepth.ordinal() ? depth : currentDepth;
    if (next.ordinal() > target.depth().ceiling().ordinal()) {
      throw new IllegalStateException("目标深度不能突破候选人阶段上限");
    }
    return new TargetWorkState(targetId, target, remainingBudget, next, status);
  }

  public TargetWorkState consume(WorkBudgetType budgetType) {
    return new TargetWorkState(
        targetId,
        target,
        remainingBudget.consume(budgetType),
        currentDepth,
        status
    );
  }

  public TargetWorkState withStatus(TargetWorkStatus nextStatus) {
    return new TargetWorkState(targetId, target, remainingBudget, currentDepth, nextStatus);
  }
}
