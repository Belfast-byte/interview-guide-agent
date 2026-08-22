package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.List;

/**
 * 规划技能值对象，描述面试计划中可使用的考察技能。
 */
public record PlanningSkill(String skillId, List<String> focusIds) {

  public PlanningSkill {
    focusIds = List.copyOf(focusIds);
  }
}
