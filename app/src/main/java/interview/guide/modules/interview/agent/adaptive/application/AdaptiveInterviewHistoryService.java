package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewSummaryProjection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdaptiveInterviewHistoryService {

  public static final int PAGE_SIZE = 20;
  private static final int JD_SUMMARY_MAX_LENGTH = 120;

  private final AdaptiveAgentSessionRepository sessionRepository;

  @Transactional(readOnly = true)
  public Page<AdaptiveInterviewSummary> list(UUID candidateId, int page) {
    return sessionRepository.findCandidateHistory(
        candidateId.toString(),
        PageRequest.of(page, PAGE_SIZE)
    ).map(this::toSummary);
  }

  private AdaptiveInterviewSummary toSummary(AdaptiveInterviewSummaryProjection source) {
    return new AdaptiveInterviewSummary(
        source.getSessionId(),
        source.getStatus(),
        source.getCurrentTurn(),
        source.getMaxTurns(),
        summarize(source.getJd()),
        source.getCreatedAt(),
        source.getCompletedAt()
    );
  }

  private String summarize(String jd) {
    String normalized = jd.replaceAll("\\s+", " ").trim();
    return normalized.length() <= JD_SUMMARY_MAX_LENGTH
        ? normalized
        : normalized.substring(0, JD_SUMMARY_MAX_LENGTH);
  }
}
