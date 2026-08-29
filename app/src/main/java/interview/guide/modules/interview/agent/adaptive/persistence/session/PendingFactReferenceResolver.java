package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.application.PendingAssessmentReferences;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import java.util.List;
import java.util.Map;

/** 将最终事务前公开给模型的临时事实引用解析为已保存 ID。 */
final class PendingFactReferenceResolver {

  private PendingFactReferenceResolver() {}

  static WorkingMemory resolve(
      WorkingMemory memory,
      Map<Long, Long> gapIds,
      Map<Long, Long> evidenceIds
  ) {
    var focus = new WorkingMemory.Focus(
        memory.focus().activeTargetId(),
        resolveId(memory.focus().activeGapId(), gapIds),
        memory.focus().gapPriorities().stream()
            .map(priority -> new WorkingMemory.GapPriority(
                requireId(priority.gapId(), gapIds), priority.reason()))
            .toList()
    );
    var deliberation = new WorkingMemory.Deliberation(
        memory.deliberation().hypotheses().stream()
            .map(hypothesis -> resolve(hypothesis, evidenceIds)).toList(),
        memory.deliberation().nextProbeIntent(),
        memory.deliberation().adoptedObservationRefs()
    );
    return new WorkingMemory(memory.basedOnTurnIndex(), focus, deliberation);
  }

  private static WorkingMemory.Hypothesis resolve(
      WorkingMemory.Hypothesis hypothesis,
      Map<Long, Long> evidenceIds
  ) {
    var links = hypothesis.evidenceLinks();
    return new WorkingMemory.Hypothesis(
        hypothesis.statement(),
        hypothesis.status(),
        new WorkingMemory.EvidenceLinks(
            resolveIds(links.supportingEvidenceIds(), evidenceIds),
            resolveIds(links.contradictingEvidenceIds(), evidenceIds)
        )
    );
  }

  private static List<Long> resolveIds(List<Long> ids, Map<Long, Long> savedIds) {
    return ids.stream().map(id -> requireId(id, savedIds)).toList();
  }

  private static Long resolveId(Long id, Map<Long, Long> savedIds) {
    return id == null ? null : requireId(id, savedIds);
  }

  static long requireId(long id, Map<Long, Long> savedIds) {
    if (!PendingAssessmentReferences.pending(id)) {
      return id;
    }
    Long resolved = savedIds.get(id);
    if (resolved == null) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "临时事实引用无法解析");
    }
    return resolved;
  }
}
