package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentCompletion;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStore;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.episode.ValidatedEpisodeTag;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Episode enrichment 的短事务状态与结果持久化。
 */
@Service
@RequiredArgsConstructor
public class EpisodeEnrichmentPersistenceService implements EpisodeEnrichmentStore {

  private final EpisodeFactRepository episodeRepository;
  private final EpisodeTagRepository tagRepository;
  private final SemanticMemoryPersistenceService semanticMemory;

  @Transactional
  @Override
  public Optional<EpisodeFact> claim(long episodeId) {
    EpisodeFactEntity episode = findLocked(episodeId);
    if (!episode.claimEnrichment()) {
      return Optional.empty();
    }
    return Optional.of(episodeRepository.saveAndFlush(episode).toDomain());
  }

  @Transactional
  @Override
  public void complete(EpisodeEnrichmentCompletion completion) {
    EpisodeFactEntity episode = findLocked(completion.episodeId());
    episode.completeEnrichment(completion.answerSummary());
    tagRepository.deleteByEpisodeId(completion.episodeId());
    tagRepository.saveAllAndFlush(toEntities(episode, completion.tags()));
    episodeRepository.saveAndFlush(episode);
    semanticMemory.refreshForEpisode(completion.episodeId());
  }

  @Transactional
  @Override
  public void fail(long episodeId, String error) {
    EpisodeFactEntity episode = findLocked(episodeId);
    episode.failEnrichment(error);
    tagRepository.deleteByEpisodeId(episodeId);
    episodeRepository.saveAndFlush(episode);
  }

  private EpisodeFactEntity findLocked(long episodeId) {
    return episodeRepository.findLockedById(episodeId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND,
            "EpisodeFact 不存在"
        ));
  }

  private List<EpisodeTagEntity> toEntities(
      EpisodeFactEntity episode,
      List<ValidatedEpisodeTag> tags
  ) {
    return tags.stream()
        .map(tag -> new EpisodeTagEntity(episode, tag.value(), tag.source()))
        .toList();
  }
}
