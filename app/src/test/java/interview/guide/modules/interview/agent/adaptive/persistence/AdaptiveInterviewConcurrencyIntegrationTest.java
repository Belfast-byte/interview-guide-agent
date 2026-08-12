package interview.guide.modules.interview.agent.adaptive.persistence;

import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdaptiveInterviewPersistenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdaptiveInterviewConcurrencyIntegrationTest {

  @Autowired
  private AdaptiveInterviewPersistenceService persistenceService;

  @Test
  @DisplayName("两个并发回答只能推进同一会话一次")
  void shouldAdvanceOnceForConcurrentAnswers()
      throws InterruptedException, TimeoutException {
    persistenceService.create(
        "concurrent-session",
        "JD",
        "Resume",
        null,
        6,
        RespondAction.ask("第一题？", "验证基础")
    );
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<AdaptiveInterviewHistory> submission = () -> {
      ready.countDown();
      start.await();
      return persistenceService.recordDecision(
          "concurrent-session",
          new CandidateAnswer(1, "并发回答"),
          RespondAction.ask("第二题？", "继续验证")
      );
    };
    List<FutureTask<AdaptiveInterviewHistory>> submissions = List.of(
        new FutureTask<>(submission),
        new FutureTask<>(submission)
    );
    submissions.forEach(task -> Thread.startVirtualThread(task));

    assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    int successes = 0;
    List<Throwable> failures = new ArrayList<>();
    for (FutureTask<AdaptiveInterviewHistory> task : submissions) {
      try {
        task.get(5, TimeUnit.SECONDS);
        successes++;
      } catch (ExecutionException e) {
        failures.add(e.getCause());
      }
    }

    AdaptiveInterviewHistory history = persistenceService.get("concurrent-session");
    assertThat(successes).isEqualTo(1);
    assertThat(failures).hasSize(1);
    assertThat(history.session().currentTurn()).isEqualTo(2);
    assertThat(history.turns()).hasSize(2);
    assertThat(history.turns().getFirst().answer()).isEqualTo("并发回答");
  }
}
