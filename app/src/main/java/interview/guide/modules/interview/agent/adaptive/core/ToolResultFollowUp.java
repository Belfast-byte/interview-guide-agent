package interview.guide.modules.interview.agent.adaptive.core;

import java.time.LocalDateTime;

/**
 * 工具结果后续处理值对象，记录工具结果触发的追问或评估更新。
 */
public record ToolResultFollowUp(
    String resultId,
    int turnIndex,
    String responseContent,
    LocalDateTime completedAt
) {}
