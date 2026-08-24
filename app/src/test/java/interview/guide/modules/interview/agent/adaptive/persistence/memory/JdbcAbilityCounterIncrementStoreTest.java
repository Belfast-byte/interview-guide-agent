package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.memory.profile.AbilityCounterIncrementStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JdbcAbilityCounterIncrementStore.class)
class JdbcAbilityCounterIncrementStoreTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private AbilityCounterIncrementStore store;

  @Autowired
  private AbilityCounterRepository repository;

  @Test
  @DisplayName("H2 原子写入按 owner 隔离并累加 Counter")
  void shouldUpsertCounterByOwnerAndTopic() {
    MemoryOwner candidate = new MemoryOwner(null, "candidate-counter");
    MemoryOwner tenant = new MemoryOwner("tenant-1", "candidate-counter");

    store.increment(candidate, TOPIC, DepthLevel.L2);
    store.increment(candidate, TOPIC, DepthLevel.L4);
    store.increment(tenant, TOPIC, DepthLevel.L1);

    assertThat(repository.findCandidateCounter(candidate.candidateId(), TOPIC)
        .orElseThrow().toDomain()).satisfies(counter -> {
          assertThat(counter.l2Count()).isOne();
          assertThat(counter.l4Count()).isOne();
          assertThat(counter.total()).isEqualTo(2);
        });
    assertThat(repository.findTenantCounter(tenant, TOPIC)
        .orElseThrow().toDomain().l1Count()).isOne();
  }
}
