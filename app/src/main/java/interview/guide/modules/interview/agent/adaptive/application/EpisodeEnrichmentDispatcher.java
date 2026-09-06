package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentJob;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRecoveryStore;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentService;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后把 Episode enrichment 交给独立记忆执行器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpisodeEnrichmentDispatcher {

  private final EpisodeEnrichmentExecutor enrichmentExecutor;
  private final EpisodeEnrichmentService enrichmentService;
  private final EpisodeEnrichmentRecoveryStore recoveryStore;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(EpisodeEnrichmentRequested event) {
    dispatch(recoveryStore.findJob(event.episodeId()));
  }

  public void dispatch(EpisodeEnrichmentJob job) {
    try {
      enrichmentExecutor.execute(() -> run(job));
    } catch (RejectedExecutionException rejection) {
      // worker 启动前不会领取任务，数据库仍为 PENDING，恢复扫描会再次投递。
      log.warn("Episode enrichment 队列暂不可用，保留待恢复任务 episodeId={}", job.episodeId());
    }
  }

  private void run(EpisodeEnrichmentJob job) {
    try {
      enrichmentService.enrich(job.episodeId(), job.llmProvider());
    } catch (RuntimeException error) {
      log.error("Episode enrichment 执行失败 episodeId={}", job.episodeId(), error);
      throw error;
    }
  }
}
