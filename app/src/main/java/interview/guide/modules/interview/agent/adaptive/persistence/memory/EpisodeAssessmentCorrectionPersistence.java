package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Assessment 修订事务内清理 Episode enrichment，并登记提交后重补全事件。
 */
@Component
@RequiredArgsConstructor
public class EpisodeAssessmentCorrectionPersistence {

  private final EpisodeTagRepository tagRepository;
  private final EpisodeFactRepository episodeRepository;
  private final ApplicationEventPublisher eventPublisher;

  public void reset(EpisodeFactEntity episode, String llmProvider) {
    episode.resetEnrichmentAfterAssessmentCorrection();
    tagRepository.deleteByEpisodeId(episode.id());
    episodeRepository.save(episode);
    eventPublisher.publishEvent(new EpisodeEnrichmentRequested(
        episode.id(),
        llmProvider
    ));
  }
}
