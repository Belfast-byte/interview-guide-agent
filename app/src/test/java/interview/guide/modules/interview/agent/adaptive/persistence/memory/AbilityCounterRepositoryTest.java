package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AbilityCounterRepositoryTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private AbilityCounterRepository repository;

  @Test
  @DisplayName("Counter 经数据库往返后保留五级计数")
  void shouldPersistCounts() {
    AbilityCounterEntity counter = new AbilityCounterEntity(
        new MemoryOwner(null, "candidate-1"),
        TOPIC
    );
    counter.increment(DepthLevel.L2);
    counter.increment(DepthLevel.L4);

    repository.saveAndFlush(counter);

    AbilityCounterEntity reloaded = repository.findCandidateCounter("candidate-1", TOPIC)
        .orElseThrow();
    assertThat(reloaded.toDomain().l2Count()).isEqualTo(1);
    assertThat(reloaded.toDomain().l4Count()).isEqualTo(1);
  }

  @Test
  @DisplayName("租户 owner 下相同 TopicKey 不允许重复")
  void shouldRejectDuplicateOwnerTopic() {
    MemoryOwner owner = new MemoryOwner("tenant-1", "candidate-1");
    repository.saveAndFlush(new AbilityCounterEntity(owner, TOPIC));

    assertThatThrownBy(() -> repository.saveAndFlush(
        new AbilityCounterEntity(owner, TOPIC)
    )).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("相同候选人在不同 tenant 下计数隔离")
  void shouldIsolateTenantCounters() {
    repository.saveAndFlush(new AbilityCounterEntity(
        new MemoryOwner("tenant-a", "candidate-1"),
        TOPIC
    ));
    repository.saveAndFlush(new AbilityCounterEntity(
        new MemoryOwner("tenant-b", "candidate-1"),
        TOPIC
    ));

    assertThat(repository.findTenantCounter(
        new MemoryOwner("tenant-a", "candidate-1"),
        TOPIC
    )).isPresent();
  }
}
