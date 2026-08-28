package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.time.LocalDateTime;
import java.util.List;

public record PracticeSemanticState(
    SemanticStateKey key,
    long revision,
    PracticeStatistics statistics,
    PracticeMastery mastery,
    List<StablePattern> stablePatterns,
    TransferAssessment transfer,
    LocalDateTime updatedAt
) implements SemanticState {

  public PracticeSemanticState {
    stablePatterns = List.copyOf(stablePatterns);
  }
}
