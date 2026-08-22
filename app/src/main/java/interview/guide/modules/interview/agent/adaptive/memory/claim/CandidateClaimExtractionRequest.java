package interview.guide.modules.interview.agent.adaptive.memory.claim;

import interview.guide.modules.interview.agent.adaptive.core.context.PlanningSkill;
import interview.guide.modules.interview.agent.adaptive.memory.brief.DimensionBriefTurn;
import java.util.List;

/**
 * 候选人声明抽取请求。
 */
public record CandidateClaimExtractionRequest(
    String sessionId,
    List<DimensionBriefTurn> turns,
    List<PlanningSkill> skillCatalog
) {

  public CandidateClaimExtractionRequest {
    turns = List.copyOf(turns);
    skillCatalog = List.copyOf(skillCatalog);
  }
}
