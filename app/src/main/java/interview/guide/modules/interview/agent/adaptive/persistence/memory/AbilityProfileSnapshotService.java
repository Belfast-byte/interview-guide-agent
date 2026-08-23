package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshotCreation;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从确定性 Counter 生成不可变 Profile 快照。
 */
@Service
@RequiredArgsConstructor
public class AbilityProfileSnapshotService {

  private final CandidateAbilityProfileRepository profileRepository;
  private final AbilityCounterRepository counterRepository;

  @Transactional
  public void snapshotCompletedSession(
      AdaptiveAgentSessionEntity session,
      List<AdaptiveAgentPlanEntity> dimensions
  ) {
    if (session.status() != AdaptiveSessionStatus.COMPLETED) {
      throw new IllegalStateException("只有已完成会话可以生成 Profile");
    }
    MemoryOwner owner = new MemoryOwner(session.tenantId(), session.candidateId());
    dimensions.stream()
        .map(AdaptiveAgentPlanEntity::topic)
        .distinct()
        .forEach(topic -> snapshotCompletedTopic(session.id(), owner, topic));
  }

  @Transactional
  public void snapshotAssessmentCorrection(
      AdaptiveAgentSessionEntity session,
      TopicKey topic
  ) {
    if (session.status() != AdaptiveSessionStatus.COMPLETED) {
      return;
    }
    MemoryOwner owner = new MemoryOwner(session.tenantId(), session.candidateId());
    AbilityCounter counter = findCounter(owner, topic)
        .orElseThrow(() -> new IllegalStateException("Assessment 修订缺少 AbilityCounter"));
    snapshot(new AbilityProfileSnapshotCreation(
        owner,
        topic,
        counter,
        session.id(),
        AbilityProfileRevisionReason.ASSESSMENT_CORRECTED
    ));
  }

  private void snapshotCompletedTopic(
      String sessionId,
      MemoryOwner owner,
      TopicKey topic
  ) {
    if (profileRepository.existsBySource(
        sessionId,
        topic,
        AbilityProfileRevisionReason.SESSION_COMPLETED
    )) {
      return;
    }
    findCounter(owner, topic).ifPresent(counter -> snapshot(
        new AbilityProfileSnapshotCreation(
            owner,
            topic,
            counter,
            sessionId,
            AbilityProfileRevisionReason.SESSION_COMPLETED
        )
    ));
  }

  private void snapshot(AbilityProfileSnapshotCreation creation) {
    current(creation.owner(), creation.topic()).ifPresent(profile -> {
      profile.supersede(LocalDateTime.now());
      profileRepository.saveAndFlush(profile);
    });
    profileRepository.save(new CandidateAbilityProfileEntity(creation));
  }

  private Optional<AbilityCounter> findCounter(MemoryOwner owner, TopicKey topic) {
    Optional<AbilityCounterEntity> entity = owner.tenantId() == null
        ? counterRepository.findCandidateCounter(owner.candidateId(), topic)
        : counterRepository.findTenantCounter(owner, topic);
    return entity.map(AbilityCounterEntity::toDomain)
        .filter(counter -> counter.total() > 0);
  }

  private Optional<CandidateAbilityProfileEntity> current(
      MemoryOwner owner,
      TopicKey topic
  ) {
    if (owner.tenantId() == null) {
      return profileRepository.findCurrentCandidateProfile(owner.candidateId(), topic);
    }
    return profileRepository.findCurrentTenantProfile(owner, topic);
  }
}
