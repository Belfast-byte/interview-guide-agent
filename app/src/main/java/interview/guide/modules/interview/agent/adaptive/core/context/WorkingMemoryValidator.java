package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.Collection;

/** 只验证 WorkingMemory 引用来自当前上下文，不评判模型的注意力选择。 */
public class WorkingMemoryValidator {

  public void validate(WorkingMemory memory, WorkingMemoryReferences references) {
    WorkingMemoryReferences.ContextIds ids = references.contextIds();
    requireAllowed(memory.basedOnTurnIndex(), ids.turnIndexes(), "Turn");
    requireAllowed(memory.focus().activeTargetId(), ids.targetIds(), "Target");
    requireAllowed(memory.focus().activeGapId(), ids.gapIds(), "Gap");
    for (WorkingMemory.GapPriority priority : memory.focus().gapPriorities()) {
      requireAllowed(priority.gapId(), ids.gapIds(), "Gap");
    }
    for (WorkingMemory.Hypothesis hypothesis : memory.deliberation().hypotheses()) {
      hypothesis.evidenceLinks().supportingEvidenceIds()
          .forEach(id -> requireAllowed(id, references.evidenceIds(), "Evidence"));
      hypothesis.evidenceLinks().contradictingEvidenceIds()
          .forEach(id -> requireAllowed(id, references.evidenceIds(), "Evidence"));
    }
    memory.deliberation().adoptedObservationRefs()
        .forEach(ref -> requireAllowed(ref, references.observationRefs(), "Observation"));
  }

  private static <T> void requireAllowed(T value, Collection<T> allowed, String type) {
    if (value != null && !allowed.contains(value)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, type + " 引用不在当前上下文中");
    }
  }
}
