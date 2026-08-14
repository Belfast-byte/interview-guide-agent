package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

public record PlannerContext(
    String jd,
    String resume,
    List<CoveredTopic> coveredTopics,
    List<UnverifiedClaim> unverifiedClaims,
    List<PlanningSkill> skillCatalog
) {

  public PlannerContext {
    coveredTopics = List.copyOf(coveredTopics);
    unverifiedClaims = List.copyOf(unverifiedClaims);
    skillCatalog = List.copyOf(skillCatalog);
  }
}
