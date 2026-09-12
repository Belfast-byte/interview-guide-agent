package interview.guide.modules.interview.agent.adaptive.core.context;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.Collection;

/** 只验证 WorkingMemory 引用来自当前上下文，不评判模型的注意力选择。 */
public class WorkingMemoryValidator {

  public void validate(WorkingMemory memory, WorkingMemoryReferences references) {
    require(memory != null && memory.focus() != null && memory.deliberation() != null,
        "WorkingMemory 必须包含 focus 和 deliberation");
    require(memory.focus().gapPriorities() != null
        && memory.deliberation().hypotheses() != null
        && memory.deliberation().adoptedObservationRefs() != null,
        "WorkingMemory 数组字段不能为空");
    WorkingMemoryReferences.ContextIds ids = references.contextIds();
    requireAllowed(memory.basedOnTurnIndex(), ids.turnIndexes(), "Turn");
    requireAllowed(memory.focus().activeTargetId(), ids.targetIds(), "Target");
    requireAllowed(memory.focus().activeGapId(), ids.gapIds(), "Gap");
    for (WorkingMemory.GapPriority priority : memory.focus().gapPriorities()) {
      require(priority != null, "Gap priority 不能为空");
      requireAllowed(priority.gapId(), ids.gapIds(), "Gap");
    }
    for (WorkingMemory.Hypothesis hypothesis : memory.deliberation().hypotheses()) {
      require(hypothesis != null && hypothesis.evidenceLinks() != null,
          "Hypothesis 必须包含 evidenceLinks");
      require(hypothesis.evidenceLinks().supportingEvidenceIds() != null
          && hypothesis.evidenceLinks().contradictingEvidenceIds() != null,
          "Evidence 引用数组不能为空");
      hypothesis.evidenceLinks().supportingEvidenceIds()
          .forEach(id -> requireReference(id, references.evidenceIds(), "Evidence"));
      hypothesis.evidenceLinks().contradictingEvidenceIds()
          .forEach(id -> requireReference(id, references.evidenceIds(), "Evidence"));
    }
    memory.deliberation().adoptedObservationRefs()
        .forEach(ref -> requireReference(ref, references.observationRefs(), "Observation"));
  }

  private static <T> void requireReference(T value, Collection<T> allowed, String type) {
    require(value != null, type + " 引用数组元素不能为空");
    requireAllowed(value, allowed, type);
  }

  private static void require(boolean valid, String message) {
    if (!valid) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, message);
    }
  }

  private static <T> void requireAllowed(T value, Collection<T> allowed, String type) {
    if (value != null && !allowed.contains(value)) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, type + " 引用不在当前上下文中");
    }
  }
}
