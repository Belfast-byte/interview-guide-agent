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
  private final interview.guide.modules.interview.agent.adaptive.persistence.memory.JpaMemoryEvidenceService memoryEvidence;
  private final org.springframework.beans.factory.ObjectProvider<interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentContextSource> contextSource;
  private final interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties properties;

  @Transactional
  @Override
  public Optional<EpisodeEnrichmentStore.Claim> claim(long episodeId) {
    EpisodeFactEntity episode = findLocked(episodeId);
    if (!episode.claimEnrichment(properties.getEpisodeEnrichmentProcessingTimeout())) {
      return Optional.empty();
    }
    episodeRepository.saveAndFlush(episode);
    return Optional.of(new EpisodeEnrichmentStore.Claim(episode.toDomain(), episode.enrichmentExecutionToken()));
  }

  @Transactional
  @Override
  public boolean complete(EpisodeEnrichmentCompletion completion) {
    EpisodeFactEntity episode = findLocked(completion.episodeId());
    if (!episode.ownsEnrichment(completion.executionToken())) return false;
    if (completion.input()!=null && completion.input().memory()!=null) {
      var current=contextSource.getObject().load(completion.episodeId());
      if (!memoryEvidence.fingerprint(current).equals(memoryEvidence.fingerprint(completion.input()))) {
        episode.recoverStaleEnrichment();
        return false;
      }
      memoryEvidence.append(episode,completion);
    }
    episode.completeEnrichment(completion.answerSummary());
    tagRepository.deleteByEpisodeId(completion.episodeId());
    tagRepository.saveAllAndFlush(toEntities(episode, completion.tags()));
    episodeRepository.saveAndFlush(episode);
    return true;
  }

  @Transactional
  @Override
  public void fail(long episodeId, String executionToken, String error) {
    EpisodeFactEntity episode = findLocked(episodeId);
    if (!episode.ownsEnrichment(executionToken)) return;
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
