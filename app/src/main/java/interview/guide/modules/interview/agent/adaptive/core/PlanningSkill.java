package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record PlanningSkill(String skillId, List<String> focusIds) {

  public PlanningSkill {
    focusIds = List.copyOf(focusIds);
  }
}
