package interview.guide.modules.interview.agent.adaptive.memory.episode;

import java.util.Optional;

/**
 * Episode enrichment 短事务写入端口。
 */
public interface EpisodeEnrichmentStore {

  Optional<EpisodeFact> claim(long episodeId);

  void complete(EpisodeEnrichmentCompletion completion);

  void fail(long episodeId, String error);
}
