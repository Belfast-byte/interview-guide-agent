package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后把 Episode enrichment 交给答题后台执行器。
 */
@Component
@RequiredArgsConstructor
public class EpisodeEnrichmentDispatcher {

  private final AdaptiveInterviewAnswerExecutor answerExecutor;
  private final EpisodeEnrichmentService enrichmentService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequested(EpisodeEnrichmentRequested event) {
    answerExecutor.execute(() -> enrichmentService.enrich(
        event.episodeId(),
        event.llmProvider()
    ));
  }
}
