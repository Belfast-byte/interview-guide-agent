package interview.guide.modules.interview.agent.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.ai.StructuredOutputProperties;
import interview.guide.common.config.LlmProviderProperties;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveAgentProperties;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Timeout(20)
class QuestionReviewServiceTest {
  private final ObjectMapper json = new ObjectMapper();

  static QuestionSnapshot sample() {
    return new QuestionSnapshot("sample", QuestionSnapshot.Source.FIXTURE, 1, "TEXT",
        new QuestionSnapshot.PublicQuestion("请证明任何负载下加索引都能提高数据库性能", "", List.of()),
        "数据库性能权衡", null, List.of(), true, "unknown", "unknown");
  }

  static QuestionReviewService.JudgeOutput clean() {
    return new QuestionReviewService.JudgeOutput(Arrays.stream(QuestionReviewService.Dimension.values())
        .map(d -> new QuestionReviewService.Observation(d, QuestionReviewService.Finding.NO_ISSUE_FOUND,
            "仅用于协议测试，不代表真实语义判定", List.of())).toList());
  }

  @Test
  void immutableSnapshotRejectsFutureHistoryAndHashesContext() {
    var context = new ArrayList<>(List.of("public context"));
    var question = new QuestionSnapshot.PublicQuestion("question", null, context);
    context.clear();
    assertThat(question.context()).containsExactly("public context");
    assertThatThrownBy(() -> new QuestionSnapshot("id", QuestionSnapshot.Source.FIXTURE, 2, "TEXT",
        question, null, null, List.of(new QuestionSnapshot.History(2, "future", null, null)), true, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    var changed = new QuestionSnapshot("sample", QuestionSnapshot.Source.FIXTURE, 1, "TEXT",
        new QuestionSnapshot.PublicQuestion(sample().publicQuestion().content(), "", List.of("new contract")),
        sample().target(), null, List.of(), true, "unknown", "unknown");
    assertThat(changed.hash(json)).isNotEqualTo(sample().hash(json));
    assertThat(changed.publicHash(json)).isNotEqualTo(sample().publicHash(json));
  }

  @Test
  void validatesDimensionsQuotesAndAmbiguousOccurrence() {
    assertThat(QuestionReviewService.validate(sample(), clean())).isEmpty();
    assertThatThrownBy(() -> QuestionReviewService.validate(sample(),
        new QuestionReviewService.JudgeOutput(java.util.Collections.nCopies(6, clean().observations().getFirst()))))
        .isInstanceOf(IllegalArgumentException.class);
    var observations = new ArrayList<>(clean().observations());
    var issue = new QuestionReviewService.Issue(QuestionReviewService.Severity.HIGH, "publicQuestion.content",
        "不存在的摘录", null, "问题", "反例", "建议");
    observations.set(0, new QuestionReviewService.Observation(QuestionReviewService.Dimension.ANSWERABILITY,
        QuestionReviewService.Finding.ISSUES_FOUND, "reason", List.of(issue)));
    assertThatThrownBy(() -> QuestionReviewService.validate(sample(), new QuestionReviewService.JudgeOutput(observations)))
        .isInstanceOf(IllegalArgumentException.class);
    var repeated = new QuestionSnapshot("duplicate", QuestionSnapshot.Source.FIXTURE, 1, "TEXT",
        new QuestionSnapshot.PublicQuestion("重复😀重复", null, List.of()), null, null, List.of(), true, null, null);
    var ambiguous = new QuestionReviewService.Issue(issue.severity(), issue.sourcePath(), "重复", null,
        issue.issue(), issue.scenario(), issue.suggestion());
    observations.set(0, new QuestionReviewService.Observation(QuestionReviewService.Dimension.ANSWERABILITY,
        QuestionReviewService.Finding.ISSUES_FOUND, "reason", List.of(ambiguous)));
    assertThatThrownBy(() -> QuestionReviewService.validate(repeated, new QuestionReviewService.JudgeOutput(observations)))
        .isInstanceOf(IllegalArgumentException.class);
    var located = new QuestionReviewService.Issue(issue.severity(), issue.sourcePath(), "重复", 2,
        issue.issue(), issue.scenario(), issue.suggestion());
    observations.set(0, new QuestionReviewService.Observation(QuestionReviewService.Dimension.ANSWERABILITY,
        QuestionReviewService.Finding.ISSUES_FOUND, "reason", List.of(located)));
    assertThat(QuestionReviewService.validate(repeated, new QuestionReviewService.JudgeOutput(observations)))
        .singleElement().satisfies(value -> assertThat(value.startOffset()).isEqualTo(4));
  }

  @Test
  void actualHttpRequestHasNoToolsAndCapturesUsageWithoutRetries() throws Exception {
    try (var fixture = new Endpoint(200, json.writeValueAsString(clean()), 0)) {
      var service = service(fixture);
      var result = service.evaluate(sample(), options(Duration.ofSeconds(8), 20000));
      assertThat(result.status()).isEqualTo(QuestionReviewService.Status.COMPLETED);
      assertThat(result.tokens().total()).isEqualTo(13);
      assertThat(result.requestsAttempted()).isEqualTo(1);
      assertThat(fixture.requests).hasSize(1);
      var request = fixture.requests.getFirst();
      assertThat(request.has("tools")).isFalse();
      assertThat(request.path("max_tokens").asInt()).isEqualTo(2000);
      assertThat(request.path("messages")).hasSize(2);
      assertThat(request.path("messages").get(1).path("content").asText())
          .doesNotContain("generatorModel", "sampleId", "verificationSource\":\"private");
      assertThat(service.evaluate(sample(), options(Duration.ofSeconds(8), 1)).status())
          .isEqualTo(QuestionReviewService.Status.SKIPPED);
      assertThat(fixture.requests).hasSize(1);
    }
    try (var fixture = new Endpoint(500, "sensitive body", 0)) {
      var result = service(fixture).evaluate(sample(), options(Duration.ofSeconds(8), 20000));
      assertThat(result.status()).isEqualTo(QuestionReviewService.Status.FAILED);
      assertThat(fixture.requests).hasSize(1);
      assertThat(json.writeValueAsString(result)).doesNotContain("sensitive body");
    }
  }

  @Test
  void excludesProvenanceAndCountsFinalSecurityPromptWithinBudget() throws Exception {
    try (var fixture = new Endpoint(200, json.writeValueAsString(clean()), 0)) {
      var snapshot = new QuestionSnapshot("later", QuestionSnapshot.Source.FIXTURE, 2, "TEXT",
          sample().publicQuestion(), sample().target(), null,
          List.of(new QuestionSnapshot.History(1, "历史公开题", "已验证的历史事实", "PRIVATE_SOURCE_ID")),
          true, "generator-secret", "prompt-private");
      var service = service(fixture);
      assertThat(service.evaluate(snapshot, options(Duration.ofSeconds(8), 20000)).status())
          .isEqualTo(QuestionReviewService.Status.COMPLETED);
      var messages = fixture.requests.getFirst().path("messages");
      assertThat(messages.toString()).doesNotContain("PRIVATE_SOURCE_ID", "generator-secret", "prompt-private");
      // Estimator in this test counts characters. The captured final system includes the security suffix.
      int actual = messages.get(0).path("content").asText().length() + 1
          + messages.get(1).path("content").asText().length();
      assertThat(service.evaluate(snapshot, options(Duration.ofSeconds(8), actual - 1)).reason())
          .isEqualTo("INPUT_BUDGET");
      var code = new QuestionSnapshot("code", QuestionSnapshot.Source.FIXTURE, 1, "CODE_REPAIR",
          sample().publicQuestion(), null, null, List.of(), true, null, null);
      assertThat(service.evaluate(code, options(Duration.ofSeconds(8), 20000)).reason()).isEqualTo("OUT_OF_SCOPE");
      var disabled = new QuestionReviewService.Options("test", "test-model", "rev", Duration.ofSeconds(8), 20000, 2000, false);
      assertThat(service.evaluate(snapshot, disabled).reason()).isEqualTo("DISABLED");
      assertThat(fixture.requests).hasSize(1);
    }
  }

  @Test
  void malformedOutputAndTimeoutFailWithoutOverwritingAnotherAttempt() throws Exception {
    try (var fixture = new Endpoint(200, "{invalid secret", 0)) {
      var service = service(fixture);
      var first = service.evaluate(sample(), options(Duration.ofSeconds(8), 20000));
      var second = service.evaluate(sample(), options(Duration.ofSeconds(8), 20000));
      assertThat(first.status()).isEqualTo(QuestionReviewService.Status.FAILED);
      assertThat(second.attempt()).isNotEqualTo(first.attempt());
      assertThat(fixture.requests).hasSize(2);
      assertThat(json.writeValueAsString(first)).doesNotContain("invalid secret");
    }
    try (var fixture = new Endpoint(200, json.writeValueAsString(clean()), 3000)) {
      var result = service(fixture).evaluate(sample(), options(Duration.ofMillis(1000), 20000));
      assertThat(result.status()).isEqualTo(QuestionReviewService.Status.FAILED);
      assertThat(result.observations()).isEmpty();
      assertThat(result.durationMillis()).isLessThan(2500);
    }
  }

  @Test
  void batchDoesNotDispatchWhileTimedOutRemoteWorkMayStillBeRunning(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    try (var fixture = new Endpoint(200, json.writeValueAsString(clean()), 0)) {
      var service = service(fixture);
      assertThat(service.evaluate(sample(), options(Duration.ofSeconds(8), 20000)).status())
          .isEqualTo(QuestionReviewService.Status.COMPLETED);
      fixture.requests.clear();
      fixture.delayMillis = 3000;
      var batch = new QuestionEvaluationBatch(service, json);
      var output = batch.run(List.of(sample(), sample()), options(Duration.ofMillis(1000), 20000), 2, directory);
      assertThat(fixture.requests).hasSize(1);
      var summary = json.readTree(output.resolve("summary.json").toFile());
      assertThat(summary.path("failed").asInt()).isEqualTo(1);
      assertThat(summary.path("skipped").asInt()).isEqualTo(1);
    }
  }

  private QuestionReviewService.Options options(Duration duration, int tokens) {
    return new QuestionReviewService.Options("test", "test-model", "test-revision", duration, tokens, 2000, true);
  }

  private QuestionReviewService service(Endpoint endpoint) {
    var config = new LlmProviderProperties.ProviderConfig();
    config.setBaseUrl("http://127.0.0.1:" + endpoint.server.getAddress().getPort());
    config.setApiKey("test-key");
    config.setModel("test-model");
    var properties = new LlmProviderProperties();
    properties.setProviders(Map.of("test", config));
    var registry = new LlmProviderRegistry(properties, null, null, null);
    var meter = new SimpleMeterRegistry();
    var telemetry = new AdaptiveAgentTelemetry(meter);
    var estimator = org.mockito.Mockito.mock(org.springframework.ai.tokenizer.TokenCountEstimator.class);
    org.mockito.Mockito.when(estimator.estimate(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(call -> ((String) call.getArgument(0)).length());
    return new QuestionReviewService(registry, new StructuredOutputInvoker(new StructuredOutputProperties(), meter),
        new AdaptiveInputTokenBudget(new AdaptiveAgentProperties(), telemetry, estimator),
        telemetry, new DeadlineExecutor(), json, new PromptLoader(new DefaultResourceLoader()));
  }

  private final class Endpoint implements AutoCloseable {
    final HttpServer server;
    volatile long delayMillis;
    final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    Endpoint(int status, String content, long delay) throws Exception {
      delayMillis = delay;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(executor);
      server.createContext("/", exchange -> {
        requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
        if (delayMillis > 0) {
          try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        byte[] body = status != 200 ? content.getBytes(StandardCharsets.UTF_8) : json.writeValueAsBytes(Map.of(
            "id", "judge", "object", "chat.completion", "created", 0, "model", "test-model",
            "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", content))),
            "usage", Map.of("prompt_tokens", 10, "completion_tokens", 3, "total_tokens", 13)));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
      });
      server.start();
    }
    @Override public void close() {
      server.stop(0);
      executor.shutdownNow();
    }
  }
}
