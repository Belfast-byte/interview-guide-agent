package interview.guide.modules.interview.agent.adaptive.memory.episode;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DB 驱动的 enrichment 恢复端口。
 */
public interface EpisodeEnrichmentRecoveryStore {

  EpisodeEnrichmentJob findJob(long episodeId);

  List<EpisodeEnrichmentJob> recoverStaleAndFindPending(LocalDateTime processingCutoff);

  EpisodeEnrichmentJob retry(MemoryOwner owner, long episodeId);
}
