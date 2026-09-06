package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.List;

/**
 * 已校验的 enrichment 替换写入参数。
 */
public record EpisodeEnrichmentCompletion(
    long episodeId,
    String executionToken,
    String answerSummary,
    List<ValidatedEpisodeTag> tags,
    EpisodeEnrichmentRequest input,
    interview.guide.modules.interview.agent.adaptive.memory.observation.MemoryObservationProposal observation,
    String provider
) {

  public EpisodeEnrichmentCompletion(long episodeId,String executionToken,String answerSummary,
      List<ValidatedEpisodeTag> tags) {
    this(episodeId,executionToken,answerSummary,tags,null,null,null);
  }

  public EpisodeEnrichmentCompletion {
    tags = List.copyOf(tags);
  }
}
