package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;

public interface PracticeMemoryOwnerSource {

  MemoryOwner findOwner(String sessionId);
}
