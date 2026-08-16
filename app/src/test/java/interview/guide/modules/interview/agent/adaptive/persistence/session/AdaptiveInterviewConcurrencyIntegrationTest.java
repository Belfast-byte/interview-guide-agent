package interview.guide.modules.interview.agent.adaptive.persistence.session;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.ValidatedAssessmentEvidence;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewHistory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.InterviewPlan;
import interview.guide.modules.interview.agent.adaptive.planning.PlanProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
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
        "candidate-1",
        "JD",
        "Resume",
        null,
        InterviewPlan.decide(
            "concurrent-session",
            new PlanProposal(List.of(
                new DimensionProposal(
                    "专业基础", "缓存", "REDIS", 2, List.of(), "java-backend"
                ),
                new DimensionProposal(
                    "项目经验", "取舍", "PROJECT", 2, List.of(), "java-backend"
                )
            ))
        ),
        RespondAction.ask("第一题？", "验证基础"),
        List.of()
    );
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<PlannedInterview> submission = () -> {
      ready.countDown();
      start.await();
      return persistenceService.recordDecision(
          "concurrent-session",
          new CandidateAnswer(1, "并发回答"),
          RespondAction.ask("第二题？", "继续验证"),
          List.of(),
          null,
          List.of(),
          new AssessmentDecision(
              "concurrent-session",
              1,
              DepthLevel.L2,
              0.8,
              "描述了实际应用",
              false,
              List.of("并发回答")
          ),
          List.of(new ValidatedAssessmentEvidence(
              EvidenceType.QUOTE,
              "并发回答",
              null
          )),
          List.of()
      );
    };
    List<FutureTask<PlannedInterview>> submissions = List.of(
        new FutureTask<>(submission),
        new FutureTask<>(submission)
    );
    submissions.forEach(task -> Thread.startVirtualThread(task));

    assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    int successes = 0;
    List<Throwable> failures = new ArrayList<>();
    for (FutureTask<PlannedInterview> task : submissions) {
      try {
        task.get(5, TimeUnit.SECONDS);
        successes++;
      } catch (ExecutionException e) {
        failures.add(e.getCause());
      }
    }

    AdaptiveInterviewHistory history = persistenceService.get("concurrent-session").history();
    assertThat(successes).isEqualTo(1);
    assertThat(failures).hasSize(1);
    assertThat(history.session().currentTurn()).isEqualTo(2);
    assertThat(history.turns()).hasSize(2);
    assertThat(history.turns().getFirst().answer()).isEqualTo("并发回答");
  }
}
