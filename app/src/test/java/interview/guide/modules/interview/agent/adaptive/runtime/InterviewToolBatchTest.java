package interview.guide.modules.interview.agent.adaptive.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolCallback;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolContext;
import interview.guide.modules.interview.agent.adaptive.tool.ReadToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.json.JsonMapper;

@Timeout(10)
class InterviewToolBatchTest {
  private final JsonMapper json = new JsonMapper();

  @Test
  void overlapsIndependentReadsWithinLimitAndKeepsEveryCallIdDespiteFailure() {
    var queries = new Queries();
    try (var scope = scope(Duration.ofSeconds(5), 8)) {
      var history = batch(2).execute(prompt(queries, scope), response("slow", "bad", "fast"), scope);
      var responses = ((ToolResponseMessage) history.getLast()).getResponses();
      assertThat(queries.peak.get()).isEqualTo(2);
      assertThat(responses).extracting(ToolResponseMessage.ToolResponse::id)
          .containsExactly("id-slow", "id-bad", "id-fast");
      assertThat(responses).extracting(r -> json.readTree(r.responseData()).path("kind").asText())
          .containsExactly("TOOL_SUCCESS", "TOOL_ERROR", "TOOL_SUCCESS");
      assertThat(scope.observations()).filteredOn(o -> o.kind() == DecisionObservation.Kind.TOOL_SUCCESS)
          .hasSize(2);
      assertThat(history).hasSize(2);
      assertThat(((AssistantMessage) history.getFirst()).getToolCalls()).hasSize(3);
    }
  }

  @Test
  void sharesDeadlineKeepsCompletedResultsAndDiscardsLateSuccess() throws Exception {
    var queries = new Queries();
    try (var scope = scope(Duration.ofMillis(600), 8)) {
      var history = batch(2).execute(prompt(queries, scope), response("uncooperative", "fast"), scope);
      var responses = ((ToolResponseMessage) history.getLast()).getResponses();
      assertThat(responses).extracting(r -> json.readTree(r.responseData()).path("kind").asText())
          .containsExactly("TOOL_TIMEOUT", "TOOL_SUCCESS");
      var before = scope.observations();
      assertThat(before).hasSize(1);
      queries.release.countDown();
      assertThat(queries.exited.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(scope.observations()).isEqualTo(before);
    } finally {
      queries.release.countDown();
    }
  }

  @Test
  void oneLocalTimeoutDoesNotCancelSiblingCalls() {
    var queries = new Queries();
    try (var scope = scope(Duration.ofSeconds(5), 8)) {
      var history = batch(2).execute(prompt(queries, scope), response("timeout", "fast"), scope);
      var responses = ((ToolResponseMessage) history.getLast()).getResponses();
      assertThat(responses).extracting(r -> json.readTree(r.responseData()).path("kind").asText())
          .containsExactly("TOOL_TIMEOUT", "TOOL_SUCCESS");
      scope.requireActive();
    }
  }

  @Test
  void concurrentAdmissionCannotExceedReadBudget() {
    var queries = new Queries();
    try (var scope = scope(Duration.ofSeconds(5), 1)) {
      var history = batch(3).execute(prompt(queries, scope), response("one", "two", "three"), scope);
      var responses = ((ToolResponseMessage) history.getLast()).getResponses();
      assertThat(responses).hasSize(3);
      assertThat(queries.executed.get()).isEqualTo(1);
      assertThat(responses.stream().filter(r -> r.responseData().contains("TOOL_SUCCESS"))).hasSize(1);
    }
  }

  @Test
  void duplicateRequestsRunOnceAndBothNativeCallIdsReceiveAResult() {
    var queries = new Queries();
    try (var scope = scope(Duration.ofSeconds(5), 8)) {
      var calls = List.of(new AssistantMessage.ToolCall("a", "function", "read", "{\"value\":\"one\"}"),
          new AssistantMessage.ToolCall("b", "function", "read", "{\"value\":\"one\"}"));
      var response = new ChatResponse(List.of(new Generation(
          AssistantMessage.builder().content("").toolCalls(calls).build())));
      var history = batch(2).execute(prompt(queries, scope), response, scope);
      var responses = ((ToolResponseMessage) history.getLast()).getResponses();
      assertThat(queries.executed.get()).isEqualTo(1);
      assertThat(responses).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("a", "b");
      assertThat(responses).extracting(r -> json.readTree(r.responseData()).path("kind").asText())
          .containsExactlyInAnyOrder("TOOL_SUCCESS", "VALIDATION_REJECTION");
    }
  }

  private InterviewToolBatch batch(int limit) {
    return new InterviewToolBatch(ToolCallingManager.builder().toolCallbackResolver(name -> null)
        .toolExecutionExceptionProcessor(e -> { throw e; }).build(), limit);
  }

  private Prompt prompt(Queries queries, InterviewToolContext scope) {
    var callback = new InterviewToolCallback(ToolCallbacks.from(queries)[0], true);
    return new Prompt(List.of(), ToolCallingChatOptions.builder().toolCallbacks(callback)
        .toolContext(scope.values()).build());
  }

  private ChatResponse response(String... values) {
    var calls = java.util.Arrays.stream(values).map(value -> new AssistantMessage.ToolCall(
        "id-" + value, "function", "read", "{\"value\":\"" + value + "\"}")).toList();
    return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
  }

  private InterviewToolContext scope(Duration timeout, int reads) {
    var context = new AgentContext(new AgentContext.SessionWindow(
        new AgentContext.SessionIdentity("session", "provider", new MemoryOwner(null, "owner")),
        SessionMode.EVALUATION, 8), new AgentContext.Facts(null, List.of(), List.of(), List.of("read")),
        WorkingMemory.empty());
    return new InterviewToolContext(context, System.nanoTime() + timeout.toNanos(), reads);
  }

  static final class Queries {
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger peak = new AtomicInteger();
    final AtomicInteger executed = new AtomicInteger();
    final CountDownLatch slowStarted = new CountDownLatch(1);
    final CountDownLatch fastFinished = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final CountDownLatch exited = new CountDownLatch(1);

    @Tool(name = "read", description = "Independent read")
    public DecisionObservation read(String value, ToolContext context) throws InterruptedException {
      executed.incrementAndGet();
      peak.accumulateAndGet(active.incrementAndGet(), Math::max);
      try {
        if (value.equals("slow")) {
          slowStarted.countDown();
          if (!fastFinished.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("queries did not overlap");
        }
        if (value.equals("bad")) {
          if (!slowStarted.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("slow read did not start");
          throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "private provider failure");
        }
        if (value.equals("timeout")) throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "local timeout");
        if (value.equals("fast")) fastFinished.countDown();
        if (value.equals("uncooperative")) {
          while (release.getCount() != 0) {
            try { release.await(); } catch (InterruptedException ignored) { /* emulate an uncooperative provider */ }
          }
        }
        return InterviewToolContext.from(context).observe("read", new ReadToolResult.Success(Map.of("value", value), List.of()));
      } finally {
        active.decrementAndGet();
        if (value.equals("uncooperative")) exited.countDown();
      }
    }
  }
}
