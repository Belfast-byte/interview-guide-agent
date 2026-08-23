package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.memory.episode.EpisodeFact;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AssessmentRevision;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assessment 等级修订对 AbilityCounter 的原子补偿。
 */
@Component
@RequiredArgsConstructor
public class AssessmentReconciliationService {

  private final AssessmentReconciliationDependencies dependencies;

  public void reconcile(AssessmentRevision revision) {
    EpisodeFactEntity episode = dependencies.episodes()
        .findBySessionIdAndTurnIndex(revision.sessionId(), revision.turnIndex())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERNAL_ERROR,
            "Assessment 修订缺少 EpisodeFact"
        ));
    if (revision.changesLevel()) {
      compensateCounter(episode, revision);
    }
    dependencies.episodeCorrection().reset(episode, revision.llmProvider());
    snapshotCorrection(episode, revision);
  }

  private void compensateCounter(
      EpisodeFactEntity episodeEntity,
      AssessmentRevision revision
  ) {
    AbilityCounterEntity counter = findCounter(episodeEntity.toDomain());
    counter.decrement(revision.oldLevel());
    counter.increment(revision.newLevel());
  }

  private void snapshotCorrection(
      EpisodeFactEntity episode,
      AssessmentRevision revision
  ) {
    if (!revision.changesLevel()) {
      return;
    }
    AdaptiveAgentSessionEntity session = dependencies.sessions()
        .findById(revision.sessionId())
        .orElseThrow(() -> new BusinessException(
            ErrorCode.INTERVIEW_SESSION_NOT_FOUND,
            "Assessment 修订缺少会话"
        ));
    dependencies.profiles().snapshotAssessmentCorrection(
        session,
        episode.toDomain().topic()
    );
  }

  private AbilityCounterEntity findCounter(EpisodeFact episode) {
    if (episode.owner().tenantId() == null) {
      return dependencies.counters().findCandidateCounter(
          episode.owner().candidateId(),
          episode.topic()
      ).orElseThrow(this::missingCounter);
    }
    return dependencies.counters().findTenantCounter(
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
