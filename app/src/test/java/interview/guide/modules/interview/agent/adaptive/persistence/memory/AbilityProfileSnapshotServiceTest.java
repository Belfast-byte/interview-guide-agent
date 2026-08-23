package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewSession;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveSessionCreation;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

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

  @Autowired
  private AbilityCounterRepository counterRepository;

  @Autowired
  private CandidateAbilityProfileRepository profileRepository;

  @Test
  @DisplayName("完成会话为每个有计数的 TopicKey 生成确定性快照")
  void shouldSnapshotAllObservedTopics() {
    saveCounter(REDIS, DepthLevel.L3);
    saveCounter(JVM, DepthLevel.L1);

    service.snapshotCompletedSession(
        session("profile-session-1"),
        List.of(plan("profile-session-1", REDIS), plan("profile-session-1", JVM))
    );

    assertThat(profileRepository
        .findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(OWNER.candidateId()))
        .extracting(entity -> entity.toDomain().ability())
        .containsExactlyInAnyOrder(SemanticAbility.PROFICIENT, SemanticAbility.WEAK);
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

  private AbilityCounterEntity saveCounter(TopicKey topic, DepthLevel level) {
    AbilityCounterEntity counter = new AbilityCounterEntity(OWNER, topic);
    counter.increment(level);
    return counterRepository.saveAndFlush(counter);
  }

  private AdaptiveAgentSessionEntity session(String sessionId) {
    return new AdaptiveAgentSessionEntity(
        new AdaptiveInterviewSession(
            sessionId,
            AdaptiveInterviewSession.RUNTIME_VERSION,
            AdaptiveSessionStatus.COMPLETED,
            FIRST_TURN,
            FIRST_TURN
        ),
        new AdaptiveSessionCreation(
            null,
            sessionId,
            OWNER.candidateId(),
            "JD",
            "Resume",
            null,
            null,
            null
        )
    );
  }

  private AdaptiveAgentPlanEntity plan(String sessionId, TopicKey topic) {
    return new AdaptiveAgentPlanEntity(sessionId, new PlannedDimension(
        FIRST_DIMENSION,
        topic.focusId(),
        topic.focusId(),
        topic.focusId(),
        FIRST_TURN,
        List.of(),
        topic.skillId(),
        FIRST_TURN,
        FIRST_TURN,
        PlanDimensionStatus.COMPLETED
    ));
  }
}
