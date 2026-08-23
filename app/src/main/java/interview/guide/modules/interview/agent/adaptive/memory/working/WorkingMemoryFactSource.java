package interview.guide.modules.interview.agent.adaptive.memory.working;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;

/** 已持久化 Working Memory 事实的只读端口。 */
public interface WorkingMemoryFactSource {

  List<ProbeGapCandidate> findProbeGaps(MemoryOwner owner, String sessionId);
}
