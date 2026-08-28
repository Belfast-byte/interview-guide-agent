package interview.guide.modules.interview.agent.adaptive.core.intent;

import java.time.LocalDateTime;

public record ActionIntentTiming(
    LocalDateTime executionStartedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
