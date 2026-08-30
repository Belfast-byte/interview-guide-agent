package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;
import java.util.List;

/**
 * 新轮次持久化参数，避免来源字段在调用点散落。
 */
public record AdaptiveTurnCreation(
    String sessionId,
    int turnIndex,
    int dimensionOrder,
    RespondAction questionAction,
    TurnProvenance provenance,
    WorkingMemory workingMemory,
    List<AdoptedRubricSource> adoptedRubrics
) {

  public AdaptiveTurnCreation {
    adoptedRubrics = List.copyOf(adoptedRubrics);
  }

  public AdaptiveTurnCreation(
      String sessionId,
      int turnIndex,
      int dimensionOrder,
      RespondAction questionAction,
      TurnProvenance provenance,
      WorkingMemory workingMemory
  ) {
    this(
        sessionId, turnIndex, dimensionOrder, questionAction,
        provenance, workingMemory, List.of());
  }

  public AdaptiveTurnCreation(
      String sessionId,
      int turnIndex,
      int dimensionOrder,
      RespondAction questionAction,
      TurnProvenance provenance
  ) {
    this(sessionId, turnIndex, dimensionOrder, questionAction, provenance, null, List.of());
  }
}
