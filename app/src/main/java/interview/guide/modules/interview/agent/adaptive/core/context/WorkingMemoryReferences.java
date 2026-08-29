package interview.guide.modules.interview.agent.adaptive.core.context;

import java.util.Set;

/** 当前 AgentContext 中可由 WorkingMemory 引用的事实标识。 */
public record WorkingMemoryReferences(
    Set<Integer> turnIndexes,
    Set<String> targetIds,
    Set<Long> gapIds,
    Set<Long> evidenceIds,
    Set<String> observationRefs
) {

  public WorkingMemoryReferences {
    turnIndexes = Set.copyOf(turnIndexes);
    targetIds = Set.copyOf(targetIds);
    gapIds = Set.copyOf(gapIds);
    evidenceIds = Set.copyOf(evidenceIds);
    observationRefs = Set.copyOf(observationRefs);
  }
}
