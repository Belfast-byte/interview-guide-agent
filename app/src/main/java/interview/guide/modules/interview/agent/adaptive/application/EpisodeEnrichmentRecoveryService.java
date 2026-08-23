package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentJob;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRecoveryStore;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 在事务外编排 enrichment 恢复、显式重试与本机队列投递。
 */
@Service
@RequiredArgsConstructor
public class EpisodeEnrichmentRecoveryService {

  private final EpisodeEnrichmentRecoveryStore recoveryStore;
  private final EpisodeEnrichmentDispatcher dispatcher;
  private final AdaptiveAgentProperties properties;

  public void recover() {
    LocalDateTime cutoff = LocalDateTime.now().minus(
        properties.getEpisodeEnrichmentProcessingTimeout()
    );
    recoveryStore.recoverStaleAndFindPending(cutoff).forEach(dispatcher::dispatch);
  }

  public void retry(MemoryOwner owner, long episodeId) {
    EpisodeEnrichmentJob job = recoveryStore.retry(owner, episodeId);
    dispatcher.dispatch(job);
  }
}
