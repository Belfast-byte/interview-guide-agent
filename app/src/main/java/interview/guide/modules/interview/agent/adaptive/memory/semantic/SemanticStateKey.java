package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;

public record SemanticStateKey(
    MemoryOwner owner,
    TopicKey topic,
    SemanticTrack track
) {}
