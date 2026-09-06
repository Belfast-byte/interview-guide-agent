package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewSummary;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdaptiveInterviewHistoryPageResponse(
    List<AdaptiveInterviewSummaryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

  static AdaptiveInterviewHistoryPageResponse from(Page<AdaptiveInterviewSummary> source) {
    return new AdaptiveInterviewHistoryPageResponse(
        source.getContent().stream().map(AdaptiveInterviewSummaryResponse::from).toList(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages()
    );
  }

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
}
