package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.tool.InterviewToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import tools.jackson.databind.json.JsonMapper;

/** 只协调本批并发和结果汇合，实际工具派发、绑定及成功消息仍由原生 manager 完成。 */
@Slf4j
final class InterviewToolBatch {
  private static final JsonMapper JSON = new JsonMapper();
  private final ToolCallingManager manager;
  private final int concurrency;

  InterviewToolBatch(ToolCallingManager manager, int concurrency) {
    if (concurrency < 1) throw new IllegalArgumentException("工具并发数必须大于 0");
    this.manager = manager;
    this.concurrency = concurrency;
  }

  List<Message> execute(Prompt prompt, ChatResponse response, InterviewToolContext scope) {
    AssistantMessage assistant = response.getResult().getOutput();
    List<ToolCall> calls = assistant.getToolCalls();
    var futures = new ArrayList<Future<ToolResponse>>(calls.size());
    var results = new ArrayList<ToolResponse>(calls.size());
    // 不使用 try-with-resources：不合作的外部调用不能让 close 无限等待。
    var workers = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory());
    try {
      for (var call : calls) {
        futures.add(InterviewAgentLoop.Proposals.isProposal(call.name()) ? null
            : workers.submit(() -> executeOne(prompt, call)));
        results.add(null);
      }
      for (int i = 0; i < calls.size(); i++) {
        if (futures.get(i) != null) results.set(i, await(calls.get(i), futures.get(i), scope));
      }
      // 查询全部汇合后再串行校验提案；其可见来源已由 Loop 在批次开始时冻结。
      for (int i = 0; i < calls.size(); i++) {
        if (futures.get(i) != null) continue;
        if (System.nanoTime() >= scope.deadlineNanos()) {
          results.set(i, failure(calls.get(i), true));
          continue;
        }
        var call = calls.get(i);
        var future = workers.submit(() -> executeOne(prompt, call));
        futures.set(i, future);
        results.set(i, await(call, future, scope));
      }
      var history = new ArrayList<Message>(prompt.getInstructions());
      history.add(assistant);
      history.add(ToolResponseMessage.builder().responses(results).build());
      return List.copyOf(history);
    } finally {
      if (System.nanoTime() >= scope.deadlineNanos() || Thread.currentThread().isInterrupted()) scope.close();
      for (var future : futures) if (future != null && !future.isDone()) future.cancel(true);
      workers.shutdownNow();
    }
  }

  private ToolResponse executeOne(Prompt prompt, ToolCall call) {
    try {
      var single = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
      var result = manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(single))));
      var message = (ToolResponseMessage) result.conversationHistory().getLast();
      return message.getResponses().getFirst();
    } catch (RuntimeException e) {
      boolean timeout = e instanceof BusinessException business
          && business.getCode() == ErrorCode.AI_SERVICE_TIMEOUT.getCode();
      log.error("面试工具调用失败: toolName={}, callId={}", call.name(), call.id(), e);
      return failure(call, timeout);
    }
  }

  private ToolResponse await(ToolCall call, Future<ToolResponse> future, InterviewToolContext scope) {
    try {
      // 即使等待前一个调用耗尽时间，已经成功的独立结果也不能丢弃。
      if (future.isDone()) return future.get();
      long remaining = scope.deadlineNanos() - System.nanoTime();
      if (remaining <= 0) throw new TimeoutException();
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return failure(call, true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "Interview Agent 工具批次被中断", e);
    } catch (ExecutionException e) {
      // VM/Error 不伪装成某个工具的业务失败。
      if (e.getCause() instanceof Error error) throw error;
      throw new IllegalStateException("工具批次执行异常", e.getCause());
    }
  }

  private ToolResponse failure(ToolCall call, boolean timeout) {
    var observation = new DecisionObservation("call-" + call.id(),
        timeout ? DecisionObservation.Kind.TOOL_TIMEOUT : DecisionObservation.Kind.TOOL_ERROR,
        null, timeout ? "工具调用超时" : "工具调用失败", call.name(), Map.of(), List.of());
    return new ToolResponse(call.id(), call.name(), JSON.writeValueAsString(observation));
  }
}
