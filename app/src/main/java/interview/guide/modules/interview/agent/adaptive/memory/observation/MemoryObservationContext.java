package interview.guide.modules.interview.agent.adaptive.memory.observation;

import java.util.List;
import interview.guide.modules.interview.agent.adaptive.core.session.AdoptedRubricSource;

public record MemoryObservationContext(
    List<String> objectives, String skillReference, List<AdoptedRubricSource> adoptedRubrics,
    List<PriorTurn> sessionTurns, List<PriorObservation> priorObservations,
    boolean priorObservationsComplete, boolean followUp, List<String> priorQuestions
) {
  public MemoryObservationContext {
    priorQuestions=List.copyOf(priorQuestions);
    objectives=List.copyOf(objectives); adoptedRubrics=List.copyOf(adoptedRubrics);
    sessionTurns=List.copyOf(sessionTurns); priorObservations=List.copyOf(priorObservations);
  }
  public record PriorTurn(int turnIndex, String question, String answer) {}
  public record PriorObservation(long revisionId, String sessionId, String capabilityKey,
      String objective, String question, String summary, MemoryObservationProposal observation) {}
}
