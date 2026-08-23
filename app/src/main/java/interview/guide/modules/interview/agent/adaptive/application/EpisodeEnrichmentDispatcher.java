package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentJob;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRecoveryStore;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后把 Episode enrichment 交给答题后台执行器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpisodeEnrichmentDispatcher {

  private final AdaptiveInterviewAnswerExecutor answerExecutor;
  private final EpisodeEnrichmentService enrichmentService;
  private final EpisodeEnrichmentRecoveryStore recoveryStore;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(EpisodeEnrichmentRequested event) {
    dispatch(recoveryStore.findJob(event.episodeId()));
  }

  public void dispatch(EpisodeEnrichmentJob job) {
    answerExecutor.execute(() -> run(job));
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
