package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentJob;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentRecoveryStore;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeEnrichmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以数据库状态为事实源恢复 Episode enrichment 投递。
 */
@Service
@RequiredArgsConstructor
public class EpisodeEnrichmentRecoveryPersistence
    implements EpisodeEnrichmentRecoveryStore {

  private final EpisodeFactRepository episodeRepository;
  private final EpisodeTagRepository tagRepository;

  @Override
  @Transactional(readOnly = true)
  public EpisodeEnrichmentJob findJob(long episodeId) {
    return loadJob(episodeId);
  }

  private EpisodeEnrichmentJob loadJob(long episodeId) {
    return episodeRepository.findEnrichmentJobById(episodeId)
        .map(this::toJob)
        .orElseThrow(() -> notFound("Episode 对应 Session 不存在"));
  }

  @Override
  @Transactional
  public List<EpisodeEnrichmentJob> recoverStaleAndFindPending(
      LocalDateTime processingCutoff
  ) {
    List<EpisodeFactEntity> stale = episodeRepository
        .findByEnrichmentStatusAndUpdatedAtBeforeOrderByUpdatedAtAscIdAsc(
            EpisodeEnrichmentStatus.PROCESSING,
            processingCutoff
        );
    stale.forEach(EpisodeFactEntity::recoverStaleEnrichment);
    episodeRepository.flush();
    return episodeRepository.findEnrichmentJobsByStatus(
        EpisodeEnrichmentStatus.PENDING
    ).stream().map(this::toJob).toList();
  }

  @Override
  @Transactional
  public EpisodeEnrichmentJob retry(MemoryOwner owner, long episodeId) {
    EpisodeFactEntity episode = findOwnedLocked(owner, episodeId);
    episode.retryEnrichment();
    tagRepository.deleteByEpisodeId(episodeId);
    episodeRepository.saveAndFlush(episode);
    return loadJob(episodeId);
  }

  private EpisodeFactEntity findOwnedLocked(MemoryOwner owner, long episodeId) {
    if (owner.tenantId() == null) {
      return episodeRepository.findLockedByIdAndCandidateIdAndTenantIdIsNull(
          episodeId,
          owner.candidateId()
      ).orElseThrow(() -> notFound("EpisodeFact 不存在"));
    }
    return episodeRepository.findLockedByIdAndTenantIdAndCandidateId(
        episodeId,
        owner.tenantId(),
        owner.candidateId()
    ).orElseThrow(() -> notFound("EpisodeFact 不存在"));
  }

  private EpisodeEnrichmentJob toJob(EpisodeEnrichmentJobProjection source) {
    if (source.getSessionId() == null) {
      throw notFound("Episode 对应 Session 不存在");
    }
    return new EpisodeEnrichmentJob(source.getEpisodeId(), source.getLlmProvider());
  }

  private BusinessException notFound(String message) {
    return new BusinessException(ErrorCode.NOT_FOUND, message);
  }
}
