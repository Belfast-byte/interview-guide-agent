package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;

public record SemanticOwnerTopic(
    MemoryOwner owner,
    TopicKey topic
) {}
