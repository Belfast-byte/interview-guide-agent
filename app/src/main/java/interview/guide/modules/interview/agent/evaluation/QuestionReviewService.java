package interview.guide.modules.interview.agent.evaluation;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptLoader;
import interview.guide.common.ai.PromptSecurityConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveAgentTelemetry;
import interview.guide.modules.interview.agent.adaptive.observability.AdaptiveInputTokenBudget;
import interview.guide.modules.interview.agent.adaptive.runtime.DeadlineExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;

/** 仅由离线入口显式构造；无 Spring Bean、生产调用者或持久化副作用。 */
public final class QuestionReviewService {
  public static final String RUBRIC_VERSION = "text-question-v1";
  private static final Logger LOG = LoggerFactory.getLogger(QuestionReviewService.class);
  private final LlmProviderRegistry providers;
  private final StructuredOutputInvoker invoker;
  private final AdaptiveInputTokenBudget inputBudget;
  private final AdaptiveAgentTelemetry telemetry;
  private final DeadlineExecutor deadline;
  private final ObjectMapper json;
  private final String prompt;

  public QuestionReviewService(LlmProviderRegistry providers, StructuredOutputInvoker invoker,
      AdaptiveInputTokenBudget inputBudget, AdaptiveAgentTelemetry telemetry, DeadlineExecutor deadline,
      ObjectMapper json, PromptLoader prompts) {
    this.providers = providers;
    this.invoker = invoker;
    this.inputBudget = inputBudget;
    this.telemetry = telemetry;
    this.deadline = deadline;
    this.json = json;
    this.prompt = prompts.loadText("classpath:prompts/question-quality-judge.st");
  }

  public enum Dimension { ANSWERABILITY, TECHNICAL_CORRECTNESS, ACCEPTANCE_FAIRNESS, FOCUS, LEAKAGE, REPETITION }
  public enum Finding { ISSUES_FOUND, NO_ISSUE_FOUND, INSUFFICIENT_EVIDENCE }
  public enum Severity { HIGH, MEDIUM, LOW }
  public enum Status { COMPLETED, FAILED, SKIPPED }
  public record Issue(Severity severity, String sourcePath, String quote, Integer occurrence,
      String issue, String scenario, String suggestion) {}
  public record Observation(Dimension dimension, Finding finding, String rationale, List<Issue> issues) {}
  public record JudgeOutput(List<Observation> observations) {}
  public record LocatedIssue(Dimension dimension, Issue issue, int startOffset, int endOffset) {}
  public record Tokens(Integer prompt, Integer completion, Integer total, String responseModel) {}
  public record Options(String providerId, String model, String codeRevision, Duration timeout,
      int maxInputTokens, int maxOutputTokens, boolean enabled) {
    public Options {
      QuestionSnapshot.requireText(providerId);
      QuestionSnapshot.requireText(model);
      QuestionSnapshot.requireText(codeRevision);
      if (timeout == null || timeout.isNegative() || timeout.isZero()
          || timeout.compareTo(Duration.ofMinutes(10)) > 0 || maxInputTokens < 1 || maxOutputTokens < 1) {
        throw new IllegalArgumentException("Invalid evaluation budget");
      }
    }
  }
  public record Report(String attempt, Instant startedAt, QuestionSnapshot snapshot, String snapshotHash,
      String publicHash, String rubricVersion, String promptHash, Options options, Status status, String reason,
      long durationMillis, int requestsAttempted, Tokens tokens, List<Observation> observations,
      List<LocatedIssue> locatedIssues) {}

  public Report evaluate(QuestionSnapshot snapshot, Options options) {
    long started = System.nanoTime();
    long ends = started + options.timeout().toNanos();
    Instant at = Instant.now();
    var capture = new Capture();
    Status status = Status.SKIPPED;
    String reason = !options.enabled() ? "DISABLED" : "OUT_OF_SCOPE";
    List<Observation> observations = List.of();
    List<LocatedIssue> locations = List.of();
    if (options.enabled() && "TEXT".equals(snapshot.questionType())) {
      try {
        var converter = StructuredOutputInvoker.strictConverter(JudgeOutput.class);
        String system = prompt + "\n" + converter.getFormat();
        // Metadata and provenance identifiers remain local; only allowed material enters the model.
        String user = json.writeValueAsString(Map.of("publicQuestion", snapshot.publicQuestion(),
            "target", snapshot.target() == null ? "" : snapshot.target(),
            "gap", snapshot.gap() == null ? "" : snapshot.gap(), "history", snapshot.history().stream().map(h -> Map.of(
                "turnIndex", h.turnIndex(), "question", h.question(),
                "verification", h.verification() == null ? "" : h.verification())).toList(),
            "historyComplete", snapshot.historyComplete(), "sources", snapshot.sources()));
        int estimate = inputBudget.estimate(system + PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION + "\n" + user);
        telemetry.inputTokens("question_judge", estimate);
        if (estimate > options.maxInputTokens()) {
          reason = "INPUT_BUDGET";
        } else if (System.nanoTime() >= ends) {
          reason = "DEADLINE_BEFORE_REQUEST";
        } else {
          JudgeOutput output = deadline.invoke(() -> {
            var client = providers.getSingleRequestPlainChatClient(options.providerId()).mutate()
                .defaultOptions(OpenAiChatOptions.builder().model(options.model()).maxRetries(0)
                    .timeout(Duration.ofNanos(Math.max(1, ends - System.nanoTime())))
                    .maxTokens(options.maxOutputTokens()))
                .defaultAdvisors(capture).build();
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= ends) {
              throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
            }
            return invoker.invokeOnce(telemetry.observeTokenUsage(client, "question_judge", "offline"),
                system, user, converter, ErrorCode.AI_SERVICE_ERROR, "Judge: ", "question_judge", LOG);
          }, ends, "question_judge");
          locations = validate(snapshot, output);
          observations = output.observations().stream().map(o -> new Observation(o.dimension(),
              o.finding(), o.rationale(), List.copyOf(o.issues()))).toList();
          status = Status.COMPLETED;
          reason = null;
        }
      } catch (Exception error) {
        status = Status.FAILED;
        reason = error instanceof BusinessException business && business.getCode() == ErrorCode.AI_SERVICE_TIMEOUT.getCode()
            ? "TIMEOUT" : capture.failure.get() != null ? capture.failure.get()
            : capture.tokens.get() != null ? "INVALID_OUTPUT" : "REQUEST_SETUP_FAILED";
        // Do not persist exception messages that may contain private model output.
        LOG.warn("question_judge_failed type={} stack={}", reason, java.util.Arrays.toString(error.getStackTrace()));
      }
    }
    return new Report(UUID.randomUUID().toString(), at, snapshot, snapshot.hash(json), snapshot.publicHash(json),
        RUBRIC_VERSION, QuestionSnapshot.digest(prompt), options, status, reason, (System.nanoTime() - started) / 1_000_000,
        capture.calls.get(), capture.tokens.get(), observations, locations);
  }

  static List<LocatedIssue> validate(QuestionSnapshot snapshot, JudgeOutput output) {
    if (output == null || output.observations() == null || output.observations().size() != 6) {
      throw new IllegalArgumentException("Six dimensions required");
    }
    var seen = EnumSet.noneOf(Dimension.class);
    var locations = new ArrayList<LocatedIssue>();
    for (var item : output.observations()) {
      if (item == null || item.dimension() == null || !seen.add(item.dimension()) || item.finding() == null
          || item.issues() == null || (item.finding() == Finding.ISSUES_FOUND) != !item.issues().isEmpty()) {
        throw new IllegalArgumentException("Invalid observation");
      }
      QuestionSnapshot.requireText(item.rationale());
      for (var issue : item.issues()) {
        if (issue == null || issue.severity() == null) throw new IllegalArgumentException("Invalid issue");
        QuestionSnapshot.requireText(issue.quote());
        QuestionSnapshot.requireText(issue.issue());
        QuestionSnapshot.requireText(issue.scenario());
        QuestionSnapshot.requireText(issue.suggestion());
        String text = snapshot.sources().get(issue.sourcePath());
        if (text == null) throw new IllegalArgumentException("Unknown quote source");
        int first = text.indexOf(issue.quote());
        if (first < 0) throw new IllegalArgumentException("Fabricated quote");
        int occurrence = issue.occurrence() == null ? 1 : issue.occurrence();
        if (occurrence < 1 || (issue.occurrence() == null && text.indexOf(issue.quote(), first + 1) >= 0)) {
          throw new IllegalArgumentException("Ambiguous quote");
        }
        int offset = first;
        for (int n = 1; n < occurrence && offset >= 0; n++) offset = text.indexOf(issue.quote(), offset + 1);
        if (offset < 0) throw new IllegalArgumentException("Missing occurrence");
        locations.add(new LocatedIssue(item.dimension(), issue, offset, offset + issue.quote().length()));
      }
    }
    return List.copyOf(locations);
  }

  private static final class Capture implements CallAdvisor {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Tokens> tokens = new AtomicReference<>();
    private final AtomicReference<String> failure = new AtomicReference<>();
    @Override public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
      if (calls.incrementAndGet() != 1) throw new IllegalStateException("Multiple Judge calls");
      ChatClientResponse response;
      try { response = chain.nextCall(request); }
      catch (RuntimeException error) { failure.set("PROVIDER_REQUEST_FAILED"); throw error; }
      var chat = response.chatResponse();
      if (chat != null) {
        var usage = chat.getMetadata().getUsage();
        tokens.set(new Tokens(usage == null ? null : usage.getPromptTokens(),
            usage == null ? null : usage.getCompletionTokens(), usage == null ? null : usage.getTotalTokens(),
            chat.getMetadata().getModel()));
        if (chat.hasToolCalls()) throw new IllegalArgumentException("Judge tool calls forbidden");
      }
      return response;
    }
    @Override public String getName() { return "question-judge-request-usage"; }
    @Override public int getOrder() { return Integer.MAX_VALUE - 1; }
  }
}
