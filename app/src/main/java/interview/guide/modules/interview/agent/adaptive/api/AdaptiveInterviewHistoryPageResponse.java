package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewSummary;
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
}
