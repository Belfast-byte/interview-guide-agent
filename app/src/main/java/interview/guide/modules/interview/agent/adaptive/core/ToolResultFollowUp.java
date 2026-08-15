package interview.guide.modules.interview.agent.adaptive.core;

import java.time.LocalDateTime;

public record ToolResultFollowUp(
    String resultId,
    int turnIndex,
    String responseContent,
    LocalDateTime completedAt
) {}
