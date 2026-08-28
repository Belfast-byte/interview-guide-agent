package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import java.time.LocalDateTime;

public record SemanticSource(
    long episodeId,
    MemoryOwner owner,
    TopicKey topic,
    LocalDateTime createdAt
) {}
