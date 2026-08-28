package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.util.List;

public record PracticeAggregate(
    PracticeStatistics statistics,
    PracticeMastery mastery,
    List<StablePattern> stablePatterns,
    TransferAssessment transfer
) {

  public PracticeAggregate {
    stablePatterns = List.copyOf(stablePatterns);
  }
}
