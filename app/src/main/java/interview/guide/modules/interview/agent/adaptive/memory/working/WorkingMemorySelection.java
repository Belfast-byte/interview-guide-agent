package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.session.NextTurnProvenanceDraft;
import java.util.Objects;

/** 工作记忆快照及其仅供短事务解析的来源草案。 */
public record WorkingMemorySelection(
    WorkingMemorySnapshot snapshot,
    NextTurnProvenanceDraft provenance
) {

  public WorkingMemorySelection {
    Objects.requireNonNull(snapshot, "snapshot 不能为空");
    Objects.requireNonNull(provenance, "provenance 不能为空");
  }
}
