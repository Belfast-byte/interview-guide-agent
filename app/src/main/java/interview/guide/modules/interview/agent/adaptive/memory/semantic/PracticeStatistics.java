package interview.guide.modules.interview.agent.adaptive.memory.semantic;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeAssistanceLevel;
import java.util.Map;

public record PracticeStatistics(
    Map<EpisodeAssistanceLevel, Long> completedByAssistance,
    long unresolvedCount,
    LatestPractice latest
) {

  public PracticeStatistics {
    completedByAssistance = Map.copyOf(completedByAssistance);
  }

  public long completed(EpisodeAssistanceLevel assistance) {
    return completedByAssistance.getOrDefault(assistance, 0L);
  }
}
