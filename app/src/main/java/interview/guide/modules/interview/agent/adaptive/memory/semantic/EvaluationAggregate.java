package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import java.util.List;

public record EvaluationAggregate(
    EvaluationStatistics statistics,
    EvaluatedAbility ability,
    List<StablePattern> stablePatterns
) {

  public EvaluationAggregate {
    stablePatterns = List.copyOf(stablePatterns);
  }
}
