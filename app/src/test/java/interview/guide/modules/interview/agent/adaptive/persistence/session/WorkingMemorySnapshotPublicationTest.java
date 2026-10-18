package interview.guide.modules.interview.agent.adaptive.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerAssessmentService.AnswerAssessment;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAnswerDecisionService.AnswerProgressionDecision;
import interview.guide.modules.interview.agent.adaptive.application.PendingAssessmentReferences;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.session.CodeRepairTask.QuestionType;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
import tools.jackson.databind.json.JsonMapper;

class WorkingMemorySnapshotPublicationTest extends AdaptiveAnswerPersistenceFixture {
  private final Logger logger = (Logger) LoggerFactory.getLogger("interview.working-memory.snapshot");
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final JsonMapper json = new JsonMapper();

  @BeforeEach
  void capture() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void cleanup() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  void initialAndNextSnapshotMatchDatabaseAndReplayDoesNotAppend() {
    var plan = initializeInterview();
    var owner = new MemoryOwner(null, "candidate-1");
    var answer = new CandidateAnswer(1, "使用版本号处理并发更新");
    claims.claim(SESSION_ID, owner, answer, "execution-1", Duration.ofMinutes(1));
    var assessment = new AssessmentDecision(SESSION_ID, 1, DepthLevel.L2, 0.8,
        "理解冲突", List.of(), List.of(new ProbeGap(null, "缺少推进规则")));
    var memory = new WorkingMemory(1,
        new WorkingMemory.Focus("target-0", PendingAssessmentReferences.gapId(0), List.of()),
        new WorkingMemory.Deliberation(List.of(), "验证版本推进", List.of()));
    var decision = new AgentDecision(memory, new AgentDecision.Ask("target-0", null,
        new AgentDecision.QuestionDraft("版本号如何推进？", "验证边界", List.of(),
            QuestionType.TEXT, null, null)));
    var commit = new AdaptiveAnswerTransactionService.AnswerCommit(owner, interview(plan),
        new AdaptiveAnswerTransactionService.CommitFacts(answer, new AnswerProgressionDecision(
            new AnswerAssessment(plan.dimension(0), assessment, List.of()), decision)), "execution-1");
    transactions.commit(commit);
    var stored = turns.findBySessionIdOrderByTurnIndex(SESSION_ID).stream()
        .map(AdaptiveAgentTurnEntity::workingMemory).toList();
    assertThat(stored.get(1).focus().activeGapId()).isPositive();
    assertThat(appender.list).isEmpty();

    TestTransaction.flagForCommit();
    TestTransaction.end();

    assertThat(appender.list).hasSize(2);
    for (int index = 0; index < stored.size(); index++) {
      var snapshot = json.readTree(appender.list.get(index).getFormattedMessage());
      assertThat(snapshot.path("turnIndex").asInt()).isEqualTo(index + 1);
      assertThat(json.treeToValue(snapshot.path("workingMemory"), WorkingMemory.class))
          .isEqualTo(stored.get(index));
    }
    transactions.commit(commit);
    assertThat(appender.list).hasSize(2);
  }

  @Test
  void rolledBackPublicationDoesNotWriteSnapshot() {
    initializeInterview();
    TestTransaction.flagForRollback();
    TestTransaction.end();
    assertThat(appender.list).isEmpty();
    assertThat(turns.findBySessionIdOrderByTurnIndex(SESSION_ID)).isEmpty();
  }
}
