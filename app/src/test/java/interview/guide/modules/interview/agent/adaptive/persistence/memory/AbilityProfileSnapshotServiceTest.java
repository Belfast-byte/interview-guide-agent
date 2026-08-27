package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.EVALUATION_SETTINGS;
import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testDimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AbilityProfileSnapshotService.class)
class AbilityProfileSnapshotServiceTest {

  private static final int FIRST_TURN = 1;
  private static final int FIRST_DIMENSION = 0;
  private static final MemoryOwner OWNER = new MemoryOwner(null, "candidate-snapshot");
  private static final TopicKey REDIS = new TopicKey("java-backend", "REDIS");
  private static final TopicKey JVM = new TopicKey("java-backend", "JVM");

  @Autowired
  private AbilityProfileSnapshotService service;

  @MockitoSpyBean
  private AbilityCounterRepository counterRepository;

  @MockitoSpyBean
  private CandidateAbilityProfileRepository profileRepository;

  @Test
  @DisplayName("完成会话为每个有计数的 TopicKey 生成确定性快照")
  void shouldSnapshotAllObservedTopics() {
    saveCounter(REDIS, DepthLevel.L3);
    saveCounter(JVM, DepthLevel.L1);
    clearInvocations(counterRepository);
    clearInvocations(profileRepository);

    service.snapshotCompletedSession(
        session("profile-session-1"),
        List.of(
            plan("profile-session-1", REDIS),
            plan("profile-session-1", JVM),
            plan("profile-session-1", REDIS)
        )
    );

    assertThat(profileRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(OWNER.candidateId()))
        .extracting(entity -> entity.toDomain().ability())
        .containsExactlyInAnyOrder(SemanticAbility.PROFICIENT, SemanticAbility.WEAK);
    verify(counterRepository, times(1)).findCounters(
        OWNER, Set.of("java-backend"), Set.of("REDIS", "JVM"));
    verify(profileRepository, times(1)).findProfilesBySource(
        OWNER,
        "profile-session-1",
        AbilityProfileRevisionReason.SESSION_COMPLETED
    );
    verify(profileRepository, times(1)).findCurrentProfiles(
        OWNER, Set.of("java-backend"), Set.of("REDIS", "JVM"));
  }

  @Test
  @DisplayName("同一完成会话重复刷新不产生重复 Profile")
  void shouldBeIdempotentForCompletedSession() {
    saveCounter(REDIS, DepthLevel.L2);
    AdaptiveAgentSessionEntity session = session("profile-session-idempotent");
    List<AdaptiveAgentPlanEntity> dimensions = List.of(
        plan("profile-session-idempotent", REDIS)
    );

    service.snapshotCompletedSession(session, dimensions);
    service.snapshotCompletedSession(session, dimensions);

    assertThat(profileRepository.count()).isOne();
  }

  @Test
  @DisplayName("新会话快照 supersede 同 owner TopicKey 的旧 current")
  void shouldSupersedePreviousSessionSnapshot() {
    AbilityCounterEntity counter = saveCounter(REDIS, DepthLevel.L2);
    service.snapshotCompletedSession(
        session("profile-session-old"),
        List.of(plan("profile-session-old", REDIS))
    );
    counter.increment(DepthLevel.L4);
    counterRepository.saveAndFlush(counter);

    service.snapshotCompletedSession(
        session("profile-session-new"),
        List.of(plan("profile-session-new", REDIS))
    );

    assertThat(profileRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(OWNER.candidateId()))
        .hasSize(2)
        .extracting(entity -> entity.toDomain().current())
        .containsExactly(false, true);
  }

  @Test
  @DisplayName("没有 Counter 的规划主题不生成空 Profile")
  void shouldSkipTopicWithoutCounter() {
    service.snapshotCompletedSession(
        session("profile-session-empty"),
        List.of(plan("profile-session-empty", REDIS))
    );

    assertThat(profileRepository.count()).isZero();
  }

  @Test
  @DisplayName("candidate 与 tenant owner 的批量快照严格隔离")
  void shouldIsolateOwnersWhenBatching() {
    MemoryOwner tenantOwner = new MemoryOwner("tenant-snapshot", OWNER.candidateId());
    saveCounter(OWNER, REDIS, DepthLevel.L1);
    saveCounter(tenantOwner, REDIS, DepthLevel.L4);

    service.snapshotCompletedSession(
        session("profile-session-candidate", OWNER),
        List.of(plan("profile-session-candidate", REDIS))
    );
    service.snapshotCompletedSession(
        session("profile-session-tenant", tenantOwner),
        List.of(plan("profile-session-tenant", REDIS))
    );

    assertThat(profileRepository.findCurrentCandidateProfile(OWNER.candidateId(), REDIS))
        .get()
        .extracting(entity -> entity.toDomain().ability())
        .isEqualTo(SemanticAbility.WEAK);
    assertThat(profileRepository.findCurrentTenantProfile(tenantOwner, REDIS))
        .get()
        .extracting(entity -> entity.toDomain().ability())
        .isEqualTo(SemanticAbility.PROFICIENT);
  }

  private AbilityCounterEntity saveCounter(TopicKey topic, DepthLevel level) {
    return saveCounter(OWNER, topic, level);
  }

  private AbilityCounterEntity saveCounter(
      MemoryOwner owner,
      TopicKey topic,
      DepthLevel level
  ) {
    AbilityCounterEntity counter = new AbilityCounterEntity(owner, topic);
    counter.increment(level);
    return counterRepository.saveAndFlush(counter);
  }

  private AdaptiveAgentSessionEntity session(String sessionId) {
    return session(sessionId, OWNER);
  }

  private AdaptiveAgentSessionEntity session(String sessionId, MemoryOwner owner) {
    return new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            sessionId,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            AdaptiveSessionStatus.COMPLETED,
            FIRST_TURN,
            FIRST_TURN,
            EVALUATION_SETTINGS
        ),
        new AdaptiveSessionCreation(
            owner.tenantId(),
            sessionId,
            owner.candidateId(),
            "JD",
            "Resume",
            null,
            null,
            null,
            EVALUATION_SETTINGS
        )
    );
  }

  private AdaptiveAgentPlanEntity plan(String sessionId, TopicKey topic) {
    return new AdaptiveAgentPlanEntity(sessionId, testDimension(new DimensionProposal(
        topic.focusId(),
        topic.focusId(),
        topic.focusId(),
        FIRST_TURN,
        List.of(),
        topic.skillId()
    ), FIRST_DIMENSION, FIRST_TURN));
  }
}
