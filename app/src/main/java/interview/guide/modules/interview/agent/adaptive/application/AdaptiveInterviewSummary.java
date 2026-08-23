package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.time.LocalDateTime;

public record AdaptiveInterviewSummary(
    String sessionId,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String jdSummary,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
