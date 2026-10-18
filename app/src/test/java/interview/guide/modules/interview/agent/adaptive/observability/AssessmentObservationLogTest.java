package interview.guide.modules.interview.agent.adaptive.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentContext;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentProposal;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentRequest;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthAssessmentAgent;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.SourceQuote;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnProvenance;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class AssessmentObservationLogTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger("interview.assessment.observation");
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final JsonMapper json = new JsonMapper();

  @BeforeEach void capture() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach void cleanup() {
    logger.detachAppender(appender);
    appender.stop();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test void completeAttemptIsOneJsonLineAndUnverifiedReferenceHasExplicitStatus() {
    var quote = new SourceQuote(SourceQuote.Source.ANSWER_TEXT, "CAS", null);
    var agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(DepthLevel.L3, 0.8, "解释并发机制", List.of(quote)));
    var request = new AssessmentRequest("session", 2,
        AssessmentContext.currentAnswer("Java", "并发", "解释 \"CAS\"\n及边界", "CAS 成功；CAS 失败"), "参考机制");
    var result = agent.assess(request, "test-provider");
    assertThat(appender.list).hasSize(1);
    String line = appender.list.getFirst().getFormattedMessage();
    assertThat(line).doesNotContain("\n");
    var record = json.readTree(line);
    assertThat(record.path("status").asText()).isEqualTo("PROPOSED");
    assertThat(record.path("question").asText()).isEqualTo(request.context().question());
    assertThat(record.path("userInput").path("answer").asText()).isEqualTo(request.context().answer());
    assertThat(record.path("referenceAnswer").path("skillReference").asText()).isEqualTo("参考机制");
    assertThat(record.path("referenceAnswer").path("kind").asText()).isEqualTo("ASSESSMENT_CRITERIA");
    assertThat(record.path("assessmentResult").path("depthLevel").asText()).isEqualTo("L3");
    assertThat(record.path("references").get(0).path("status").asText()).isEqualTo("UNVERIFIED");
    assertThat(record.path("observationId").asText()).isEqualTo(result.observationId());
    assertThat(record.path("modelAssessment").path("evidenceQuotes")).hasSize(1);
    assertThat(record.path("assessmentResult").path("evidenceQuotes")).isEmpty();
  }

  @Test void failedModelCallStillLogsQuestionInputAndReferences() {
    var request = new AssessmentRequest("failed-session", 1,
        AssessmentContext.currentAnswer("Java", "并发", "问题", "回答"));
    var agent = new DepthAssessmentAgent((r, p) -> { throw new IllegalStateException("provider unavailable"); });
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> agent.assess(request, "provider"))
        .hasMessage("provider unavailable");
    var record = json.readTree(appender.list.getFirst().getFormattedMessage());
    assertThat(record.path("status").asText()).isEqualTo("FAILED");
    assertThat(record.path("assessmentResult").isNull()).isTrue();
    assertThat(record.path("question").asText()).isEqualTo("问题");
    assertThat(record.path("userInput").path("answer").asText()).isEqualTo("回答");
    assertThat(record.path("errorType").asText()).isEqualTo("IllegalStateException");
  }

  @Test void publicationOnlyAppearsAfterCommitAndNeverOnRollback() {
    var turn = new AdaptiveInterviewTurn(1, 0, "问题", "考察原因", null, null, null, null, TurnProvenance.initial());
    TransactionSynchronizationManager.initSynchronization();
    AssessmentObservationLog.published("session", turn);
    assertThat(appender.list).isEmpty();
    TransactionSynchronizationManager.getSynchronizations().forEach(s ->
        s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    assertThat(appender.list).isEmpty();
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.initSynchronization();
    AssessmentObservationLog.published("session", turn);
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    var record = json.readTree(appender.list.getFirst().getFormattedMessage());
    assertThat(record.path("event").asText()).isEqualTo("question_published");
    assertThat(record.path("status").asText()).isEqualTo("COMMITTED");
    assertThat(record.path("userInput").isNull()).isTrue();
  }
}
