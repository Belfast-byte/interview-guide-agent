package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record PlannerContext(
    String jd,
    String resume,
    List<CoveredTopic> coveredTopics,
    List<PlanningSkill> skillCatalog
) {

  public PlannerContext {
    coveredTopics = List.copyOf(coveredTopics);
    skillCatalog = List.copyOf(skillCatalog);
  }
}
