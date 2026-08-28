package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.util.List;

public interface SemanticStateSource {

  List<SemanticState> findByOwner(MemoryOwner owner);
}
