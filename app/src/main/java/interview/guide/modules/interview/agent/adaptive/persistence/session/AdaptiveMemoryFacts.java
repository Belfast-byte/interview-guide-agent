package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.memory.claim.CandidateClaim;
import java.util.List;

public record AdaptiveMemoryFacts(
    DimensionBrief dimensionBrief,
    List<CandidateClaim> candidateClaims
) {

  public AdaptiveMemoryFacts {
    candidateClaims = List.copyOf(candidateClaims);
  }
}
