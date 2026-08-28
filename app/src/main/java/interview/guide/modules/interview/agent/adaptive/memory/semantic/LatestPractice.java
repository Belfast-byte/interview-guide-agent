package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import java.time.LocalDateTime;

public record LatestPractice(
    long episodeId,
    PracticeResult result,
    LocalDateTime createdAt
) {

  public PracticeMastery mastery() {
    if (result.outcome() == PracticeOutcome.UNRESOLVED) {
      return PracticeMastery.UNRESOLVED;
    }
    return result.assistance() == EpisodeAssistanceLevel.NONE
        ? PracticeMastery.INDEPENDENT
        : PracticeMastery.ASSISTED;
  }
}
