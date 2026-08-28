package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationSemanticState(
    SemanticStateKey key,
    long revision,
    EvaluationStatistics statistics,
    EvaluatedAbility ability,
    List<StablePattern> stablePatterns,
    LocalDateTime updatedAt
) implements SemanticState {

  public EvaluationSemanticState {
    stablePatterns = List.copyOf(stablePatterns);
  }
}
