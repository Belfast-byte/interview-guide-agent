package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewSummary;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.time.LocalDateTime;

public record AdaptiveInterviewSummaryResponse(
    String sessionId,
    AdaptiveSessionStatus status,
    int currentTurn,
    int maxTurns,
    String jdSummary,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {

  static AdaptiveInterviewSummaryResponse from(AdaptiveInterviewSummary summary) {
    return new AdaptiveInterviewSummaryResponse(
        summary.sessionId(),
        summary.status(),
        summary.currentTurn(),
        summary.maxTurns(),
        summary.jdSummary(),
        summary.createdAt(),
        summary.completedAt()
    );
  }
}
