package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;

public interface SemanticStateSource {

  List<SemanticState> findByOwner(MemoryOwner owner);
  default List<interview.guide.modules.interview.agent.adaptive.memory.observation.CapabilityBelief>
      beliefs(MemoryOwner owner, interview.guide.modules.interview.agent.adaptive.core.context.TopicKey topic) {
    return List.of();
  }
}
