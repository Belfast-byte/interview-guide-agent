package interview.guide.modules.interview.agent.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.persistence.plan.AdaptiveAgentPlanRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentSessionRepository;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveAgentTurnRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

@Timeout(15)
class QuestionEvaluationBoundaryTest {
  @TempDir Path temporary;
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ownershipCheckedBeforeMaterialAndHistoryExcludesFutureAnswersAndInternalReason() {
    var sessions = mock(AdaptiveAgentSessionRepository.class);
    var turns = mock(AdaptiveAgentTurnRepository.class);
    var plans = mock(AdaptiveAgentPlanRepository.class);
    var transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var reader = new PublishedQuestionSnapshotReader(sessions, turns, plans, transactions, json);
    assertThatThrownBy(() -> reader.read("owner", null, "session", 2)).isInstanceOf(RuntimeException.class);
    verifyNoInteractions(turns, plans);
    var session = mock(AdaptiveAgentSessionEntity.class);
    when(sessions.findByIdAndCandidateIdAndTenantIdIsNull("session", "owner")).thenReturn(Optional.of(session));
    var storedTurns = List.of(turn(1), turn(2), turn(3));
    when(turns.findBySessionIdOrderByTurnIndex("session")).thenReturn(storedTurns);
    when(plans.findBySessionIdOrderByDimensionOrder("session")).thenReturn(List.of());
    var snapshot = reader.read("owner", null, "session", 2);
    assertThat(snapshot.source()).isEqualTo(QuestionSnapshot.Source.PUBLISHED);
    assertThat(snapshot.history()).singleElement().satisfies(h -> {
      assertThat(h.question()).isEqualTo("question-1");
      assertThat(h.verification()).isNull();
    });
    assertThat(json.writeValueAsString(snapshot)).doesNotContain("PRIVATE_REASON", "ANSWER_SECRET", "question-3");
    assertThatThrownBy(() -> reader.read("owner", "other-tenant", "session", 2)).isInstanceOf(RuntimeException.class);
    when(sessions.findByIdAndCandidateIdAndTenantId("session", "owner", "tenant")).thenReturn(Optional.of(session));
    assertThat(reader.read("owner", "tenant", "session", 2).hash(json)).isEqualTo(snapshot.hash(json));
  }

  @Test
  void fileBoundaryRejectsPublishedSourcesUnknownFieldsAndOversize() throws Exception {
    var batch = new QuestionEvaluationBatch(mock(QuestionReviewService.class), json);
    Path file = temporary.resolve("input.json");
    Files.writeString(file, json.writeValueAsString(List.of(QuestionReviewServiceTest.sample())));
    assertThat(batch.readFixtures(file)).hasSize(1);
    Files.writeString(file, Files.readString(file).replace("FIXTURE", "PUBLISHED"));
    assertThatThrownBy(() -> batch.readFixtures(file)).isInstanceOf(IllegalArgumentException.class);
    Files.writeString(file, "[{\"reviewGuide\":\"secret\"}]");
    assertThatThrownBy(() -> batch.readFixtures(file)).isInstanceOf(RuntimeException.class);
    Files.write(file, new byte[2 * 1024 * 1024 + 1]);
    assertThatThrownBy(() -> batch.readFixtures(file)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsArePrivateAppendOnlyAndFailureDenominatorsIncludeSkippedSamples() throws Exception {
    var judge = mock(QuestionReviewService.class);
    var sample = QuestionReviewServiceTest.sample();
    when(judge.evaluate(any(), any())).thenAnswer(call -> {
      QuestionReviewService.Options options = call.getArgument(1);
      return new QuestionReviewService.Report(UUID.randomUUID().toString(), Instant.now(), sample,
          sample.hash(json), sample.publicHash(json), "v1", "prompt-hash", options,
          options.enabled() ? QuestionReviewService.Status.FAILED : QuestionReviewService.Status.SKIPPED,
          "TIMEOUT", 1, options.enabled() ? 1 : 0, null, List.of(), List.of());
    });
    var batch = new QuestionEvaluationBatch(judge, json);
    var options = new QuestionReviewService.Options("test", "model", "rev", Duration.ofSeconds(1), 100, 100, true);
    Path first = batch.run(List.of(sample, sample), options, 2, temporary);
    Path second = batch.run(List.of(sample), options, 1, temporary);
    assertThat(first).isNotEqualTo(second);
    var summary = json.readTree(first.resolve("summary.json").toFile());
    assertThat(summary.path("total").asInt()).isEqualTo(2);
    assertThat(summary.path("failed").asInt()).isEqualTo(1);
    assertThat(summary.path("skipped").asInt()).isEqualTo(1);
    assertThat(summary.path("unknownUsageCount").asInt()).isEqualTo(1);
    try (var files = Files.list(first)) {
      var skipped = files.filter(p -> p.getFileName().toString().startsWith("0002-")).findFirst().orElseThrow();
      assertThat(json.readTree(skipped.toFile()).path("reason").asText()).isEqualTo("PREVIOUS_CALL_TIMEOUT");
    }
    assertThat(Files.getPosixFilePermissions(first)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
    assertThat(Files.getPosixFilePermissions(first.resolve("summary.json")))
        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
  }

  private AdaptiveAgentTurnEntity turn(int index) {
    var entity = mock(AdaptiveAgentTurnEntity.class);
    when(entity.toDomain()).thenReturn(new AdaptiveInterviewTurn(index, 0, "question-" + index,
        "PRIVATE_REASON", "ANSWER_SECRET", null, null, null));
    return entity;
  }
}
