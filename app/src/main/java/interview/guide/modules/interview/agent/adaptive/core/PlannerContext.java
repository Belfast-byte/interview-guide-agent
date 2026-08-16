package interview.guide.modules.interview.agent.adaptive.core;

import java.util.List;

/**
 * 规划器上下文，包含 JD、简历、已覆盖主题和未验证声明，用于生成面试计划。
 */
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
