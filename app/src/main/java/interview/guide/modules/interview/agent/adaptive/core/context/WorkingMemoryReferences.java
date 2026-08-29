package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.Set;

/** 当前 AgentContext 中可由 WorkingMemory 引用的事实标识。 */
public record WorkingMemoryReferences(
    ContextIds contextIds,
    Set<Long> evidenceIds,
    Set<String> observationRefs
) {

  public WorkingMemoryReferences {
    evidenceIds = Set.copyOf(evidenceIds);
    observationRefs = Set.copyOf(observationRefs);
  }

  public record ContextIds(
      Set<Integer> turnIndexes,
      Set<String> targetIds,
      Set<Long> gapIds
  ) {

    public ContextIds {
      turnIndexes = Set.copyOf(turnIndexes);
      targetIds = Set.copyOf(targetIds);
      gapIds = Set.copyOf(gapIds);
    }
  }
}
