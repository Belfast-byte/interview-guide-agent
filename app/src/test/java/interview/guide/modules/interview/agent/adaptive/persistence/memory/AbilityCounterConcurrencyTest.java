package interview.guide.modules.interview.agent.adaptive.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AbilityCounterConcurrencyTest {

  private static final TopicKey TOPIC = new TopicKey("java-backend", "REDIS");

  @Autowired
  private AbilityCounterRepository repository;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Test
  @DisplayName("并发更新同一 Counter 时旧版本提交明确失败")
  void shouldRejectStaleCounterUpdate() {
    AbilityCounterEntity saved = repository.saveAndFlush(new AbilityCounterEntity(
        new MemoryOwner(null, "counter-conflict"),
        TOPIC
    ));
    EntityManager firstManager = entityManagerFactory.createEntityManager();
    EntityManager staleManager = entityManagerFactory.createEntityManager();
    try {
      firstManager.getTransaction().begin();
      staleManager.getTransaction().begin();
      AbilityCounterEntity first = firstManager.find(AbilityCounterEntity.class, saved.id());
      AbilityCounterEntity stale = staleManager.find(AbilityCounterEntity.class, saved.id());
      first.increment(DepthLevel.L3);
      stale.increment(DepthLevel.L4);

      firstManager.getTransaction().commit();

      assertThatThrownBy(() -> staleManager.getTransaction().commit())
          .isInstanceOfAny(RollbackException.class, OptimisticLockException.class);
      assertThat(repository.findCandidateCounter("counter-conflict", TOPIC)
          .orElseThrow().toDomain().l3Count()).isEqualTo(1);
    } finally {
      rollbackIfActive(firstManager);
      rollbackIfActive(staleManager);
      firstManager.close();
      staleManager.close();
    }
  }

  private void rollbackIfActive(EntityManager entityManager) {
    if (entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().rollback();
    }
  }
}
