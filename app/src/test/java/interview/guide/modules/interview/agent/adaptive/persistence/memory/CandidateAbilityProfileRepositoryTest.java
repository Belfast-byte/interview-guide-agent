package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityCounter;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileRevisionReason;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.AbilityProfileSnapshotCreation;
import interview.guide.modules.interview.agent.adaptive.memory.semantic.SemanticAbility;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CandidateAbilityProfileRepositoryTest {

  private static final MemoryOwner CANDIDATE = new MemoryOwner(null, "candidate-profile");
  private static final MemoryOwner TENANT_CANDIDATE = new MemoryOwner(
      "tenant-a",
      "candidate-profile"
  );
  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private CandidateAbilityProfileRepository repository;

  @Test
  @DisplayName("Profile 保存确定性 ability 和完整 L0-L4 计数快照")
  void shouldPersistCompleteCounterSnapshot() {
    CandidateAbilityProfileEntity profile = repository.saveAndFlush(profile(
        CANDIDATE,
        "session-profile-1",
        new AbilityCounter(0, 1, 2, 1, 0)
    ));

    assertThat(profile.toDomain()).satisfies(snapshot -> {
      assertThat(snapshot.owner()).isEqualTo(CANDIDATE);
      assertThat(snapshot.topic()).isEqualTo(TOPIC);
      assertThat(snapshot.ability()).isEqualTo(SemanticAbility.COMPETENT);
      assertThat(snapshot.counter()).isEqualTo(new AbilityCounter(0, 1, 2, 1, 0));
      assertThat(snapshot.revisionReason())
          .isEqualTo(AbilityProfileRevisionReason.SESSION_COMPLETED);
    });
  }

  @Test
  @DisplayName("新快照 supersede 旧 current 且历史完整保留")
  void shouldKeepHistoryAndOneCurrentSnapshot() {
    CandidateAbilityProfileEntity previous = repository.saveAndFlush(profile(
        CANDIDATE,
        "session-profile-1",
        new AbilityCounter(0, 0, 1, 0, 0)
    ));
    previous.supersede(LocalDateTime.now());
    repository.saveAndFlush(previous);
    repository.saveAndFlush(profile(
        CANDIDATE,
        "session-profile-2",
        new AbilityCounter(0, 0, 1, 1, 0)
    ));

    assertThat(repository.findByTenantIdIsNullAndCandidateIdOrderByCreatedAtAscIdAsc(
        CANDIDATE.candidateId()
    )).hasSize(2)
        .extracting(entity -> entity.toDomain().current())
        .containsExactly(false, true);
    assertThat(repository.findCurrentCandidateProfile(CANDIDATE.candidateId(), TOPIC))
        .get()
        .extracting(entity -> entity.toDomain().sourceSessionId())
        .isEqualTo("session-profile-2");
  }

  @Test
  @DisplayName("相同 candidateId 的租户与非租户 Profile 严格隔离")
  void shouldIsolateMemoryOwners() {
    repository.saveAndFlush(profile(
        CANDIDATE,
        "session-profile-candidate",
        new AbilityCounter(0, 0, 1, 0, 0)
    ));
    repository.saveAndFlush(profile(
        TENANT_CANDIDATE,
        "session-profile-tenant",
        new AbilityCounter(0, 0, 0, 1, 0)
    ));

    assertThat(repository.findCurrentCandidateProfile(CANDIDATE.candidateId(), TOPIC))
        .get()
        .extracting(entity -> entity.toDomain().ability())
        .isEqualTo(SemanticAbility.COMPETENT);
    assertThat(repository.findCurrentTenantProfile(TENANT_CANDIDATE, TOPIC))
        .get()
        .extracting(entity -> entity.toDomain().ability())
        .isEqualTo(SemanticAbility.PROFICIENT);
  }

  @Test
  @DisplayName("空 Counter 不能创建伪 Profile")
  void shouldRejectEmptyCounterSnapshot() {
    assertThatThrownBy(() -> profile(
        CANDIDATE,
        "session-empty",
        AbilityCounter.empty()
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("空 Counter");
  }

  private CandidateAbilityProfileEntity profile(
      MemoryOwner owner,
      String sessionId,
      AbilityCounter counter
  ) {
    return new CandidateAbilityProfileEntity(new AbilityProfileSnapshotCreation(
        owner,
        TOPIC,
        counter,
        sessionId,
        AbilityProfileRevisionReason.SESSION_COMPLETED
    ));
  }
}
