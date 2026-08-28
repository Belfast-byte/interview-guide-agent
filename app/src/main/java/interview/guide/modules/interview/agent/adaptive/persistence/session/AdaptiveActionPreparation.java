package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.memory.WorkStatePatch;
import java.util.List;

public record AdaptiveActionPreparation(
    AdaptiveMemoryFacts memory,
    List<WorkStatePatch> decisionPatches,
    AdaptivePlannedAction action
) {

  public AdaptiveActionPreparation {
    decisionPatches = List.copyOf(decisionPatches);
  }
}
