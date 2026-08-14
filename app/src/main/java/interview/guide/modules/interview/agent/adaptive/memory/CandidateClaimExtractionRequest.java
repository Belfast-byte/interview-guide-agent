package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.modules.interview.agent.adaptive.core.PlanningSkill;
import java.util.List;

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
