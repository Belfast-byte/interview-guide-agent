package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 读取最近 Turn 的最终 WorkingMemory Snapshot。 */
@Component
public class WorkingMemorySnapshotReader {

  private final AdaptiveAgentTurnRepository turns;

  public WorkingMemorySnapshotReader(AdaptiveAgentTurnRepository turns) {
    this.turns = turns;
  }

  @Transactional(readOnly = true)
  public WorkingMemory latest(String sessionId) {
    return turns.findFirstBySessionIdAndWorkingMemoryIsNotNullOrderByTurnIndexDesc(sessionId)
        .map(AdaptiveAgentTurnEntity::workingMemory)
        .orElse(WorkingMemory.empty());
  }
}
