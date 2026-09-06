package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Optional;

/**
 * Episode enrichment 短事务写入端口。
 */
public interface EpisodeEnrichmentStore {

  Optional<Claim> claim(long episodeId);

  record Claim(EpisodeFact episode, String executionToken) {}

  boolean complete(EpisodeEnrichmentCompletion completion);

  void fail(long episodeId, String executionToken, String error);
}
