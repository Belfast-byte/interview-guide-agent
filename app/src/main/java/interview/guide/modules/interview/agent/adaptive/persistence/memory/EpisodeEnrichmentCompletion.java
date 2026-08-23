package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.ValidatedEpisodeTag;
import java.util.List;

/**
 * 已校验的 enrichment 替换写入参数。
 */
public record EpisodeEnrichmentCompletion(
    long episodeId,
    String answerSummary,
    List<ValidatedEpisodeTag> tags
) {

  public EpisodeEnrichmentCompletion {
    tags = List.copyOf(tags);
  }
}
