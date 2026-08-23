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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    List<TopicKey> topics = dimensions.stream()
        .map(AdaptiveAgentPlanEntity::topic)
        .distinct()
        .toList();
    if (topics.isEmpty()) {
      return;
    }
    List<AbilityProfileSnapshotCreation> creations = completedCreations(
        session.id(),
        owner,
        topics
    );
    saveBatch(creations);
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

  private List<AbilityProfileSnapshotCreation> completedCreations(
      String sessionId,
      MemoryOwner owner,
      List<TopicKey> topics
  ) {
    Map<TopicKey, AbilityCounter> counters = findCounters(owner, topics);
    Set<TopicKey> existing = findCompletedSourceTopics(owner, sessionId);
    return topics.stream()
        .filter(topic -> !existing.contains(topic))
        .filter(counters::containsKey)
        .map(topic -> new AbilityProfileSnapshotCreation(
            owner,
            topic,
            counters.get(topic),
            sessionId,
            AbilityProfileRevisionReason.SESSION_COMPLETED
        ))
        .toList();
  }

  private void saveBatch(List<AbilityProfileSnapshotCreation> creations) {
    if (creations.isEmpty()) {
      return;
    }
    List<TopicKey> topics = creations.stream()
        .map(AbilityProfileSnapshotCreation::topic)
        .toList();
    Map<TopicKey, CandidateAbilityProfileEntity> current = findCurrent(
        creations.getFirst().owner(),
        topics
    );
    List<CandidateAbilityProfileEntity> superseded = supersedeCurrent(creations, current);
    if (!superseded.isEmpty()) {
      profileRepository.saveAll(superseded);
      // 先释放 partial unique current，再插入同 owner + TopicKey 的新 current。
      profileRepository.flush();
    }
    profileRepository.saveAll(creations.stream()
        .map(CandidateAbilityProfileEntity::new)
        .toList());
  }

  private List<CandidateAbilityProfileEntity> supersedeCurrent(
      List<AbilityProfileSnapshotCreation> creations,
      Map<TopicKey, CandidateAbilityProfileEntity> current
  ) {
    LocalDateTime now = LocalDateTime.now();
    List<CandidateAbilityProfileEntity> superseded = creations.stream()
        .map(AbilityProfileSnapshotCreation::topic)
        .map(current::get)
        .filter(Objects::nonNull)
        .toList();
    superseded.forEach(profile -> profile.supersede(now));
    return superseded;
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

  private Map<TopicKey, AbilityCounter> findCounters(
      MemoryOwner owner,
      List<TopicKey> topics
  ) {
    List<AbilityCounterEntity> entities = counterRepository.findCounters(
        owner,
        skillIds(topics),
        focusIds(topics)
    );
    return entities.stream()
        .filter(entity -> topics.contains(entity.topic()))
        .filter(entity -> entity.toDomain().total() > 0)
        .collect(Collectors.toUnmodifiableMap(
            AbilityCounterEntity::topic,
            AbilityCounterEntity::toDomain
        ));
  }

  private Set<TopicKey> findCompletedSourceTopics(
      MemoryOwner owner,
      String sessionId
  ) {
    List<CandidateAbilityProfileEntity> entities = profileRepository.findProfilesBySource(
        owner,
        sessionId,
        AbilityProfileRevisionReason.SESSION_COMPLETED
    );
    return entities.stream()
        .map(CandidateAbilityProfileEntity::topic)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Map<TopicKey, CandidateAbilityProfileEntity> findCurrent(
      MemoryOwner owner,
      List<TopicKey> topics
  ) {
    List<CandidateAbilityProfileEntity> entities = profileRepository.findCurrentProfiles(
        owner,
        skillIds(topics),
        focusIds(topics)
    );
    return entities.stream()
        .filter(entity -> topics.contains(entity.topic()))
        .collect(Collectors.toUnmodifiableMap(
            CandidateAbilityProfileEntity::topic,
            Function.identity()
        ));
  }

  private Set<String> skillIds(List<TopicKey> topics) {
    return topics.stream()
        .map(TopicKey::skillId)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> focusIds(List<TopicKey> topics) {
    return topics.stream()
        .map(TopicKey::focusId)
        .collect(Collectors.toUnmodifiableSet());
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
