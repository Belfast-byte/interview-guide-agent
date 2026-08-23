package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AssessmentRevision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assessment 等级修订对 AbilityCounter 的原子补偿。
 */
@Component
@RequiredArgsConstructor
public class AssessmentReconciliationService {

  private final EpisodeFactRepository episodeRepository;
  private final AbilityCounterRepository counterRepository;

  public void reconcile(AssessmentRevision revision) {
    if (!revision.changesLevel()) {
      return;
    }
    EpisodeFact episode = episodeRepository
        .findBySessionIdAndTurnIndex(revision.sessionId(), revision.turnIndex())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERNAL_ERROR,
            "Assessment 修订缺少 EpisodeFact"
        ))
        .toDomain();
    AbilityCounterEntity counter = findCounter(episode);
    counter.decrement(revision.oldLevel());
    counter.increment(revision.newLevel());
  }

  private AbilityCounterEntity findCounter(EpisodeFact episode) {
    if (episode.owner().tenantId() == null) {
      return counterRepository.findCandidateCounter(
          episode.owner().candidateId(),
          episode.topic()
      ).orElseThrow(this::missingCounter);
    }
    return counterRepository.findTenantCounter(
        episode.owner(),
        episode.topic()
    ).orElseThrow(this::missingCounter);
  }

  private BusinessException missingCounter() {
    return new BusinessException(
        ErrorCode.INTERNAL_ERROR,
        "Assessment 修订缺少 AbilityCounter"
    );
  }
}
