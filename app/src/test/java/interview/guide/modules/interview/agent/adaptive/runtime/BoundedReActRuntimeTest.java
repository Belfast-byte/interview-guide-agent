package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.action.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.context.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemorySnapshot;
import interview.guide.modules.interview.agent.adaptive.core.action.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.action.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import interview.guide.modules.interview.agent.adaptive.core.session.TurnTriggerType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedReActRuntimeTest {

  @Nested
  @DisplayName("有界 ReAct 循环")
  class Loop {

    @Test
    @DisplayName("模型直接响应时结束本次运行")
    void shouldReturnModelResponse() {
      RespondAction expected = RespondAction.ask("下一题？", "继续验证");
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          context -> expected,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      ReActResult actual = runtime.run(request(), budget(3, 1));

      assertThat(actual.response()).isEqualTo(expected);
      assertThat(actual.toolExecutions()).isEmpty();
    }

    @Test
    @DisplayName("工具结果作为 observation 回到下一模型步并形成审计事实")
    void shouldFeedToolObservationBackToModel() {
      AtomicInteger modelSteps = new AtomicInteger();
      AgentModelGateway model = context -> {
        if (modelSteps.getAndIncrement() == 0) {
          return toolCall("question_bank_search", "redis");
        }
        assertThat(context.observations()).containsExactly(new ToolObservation(
            "question_bank_search",
            Map.of("query", "redis"),
            true,
            "question:42",
            "{\"id\":42}"
        ));
        return RespondAction.ask("Redis 失效策略有哪些取舍？", "题库已返回审核题");
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      ReActResult result = runtime.run(request(), budget(3, 1));

      assertThat(result.response().content()).isEqualTo("Redis 失效策略有哪些取舍？");
      assertThat(result.toolExecutions()).extracting(ToolExecution::resultId)
          .containsExactly("question:42");
      assertThat(modelSteps).hasValue(2);
    }

    @Test
    @DisplayName("Pending 句柄立即成为 observation，循环不等待外部结果")
    void shouldContinueAfterPendingToolResult() {
      AtomicInteger modelSteps = new AtomicInteger();
      AgentModelGateway model = context -> {
        if (modelSteps.getAndIncrement() == 0) {
          return toolCall("sandbox_submit", "full");
        }
        assertThat(context.observations().getFirst().output())
            .contains("PENDING", "submission-1");
        return RespondAction.ask("先讲讲这段代码的时间复杂度？", "判题已在后台受理");
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> new ToolExecution(
              "invocation-1",
              action.toolName(),
              action.reason(),
              AgentRole.INTERVIEWER.name(),
              1,
              "keys=[runMode]",
              "submission pending",
              "submission-1",
              "{\"submissionId\":\"submission-1\",\"status\":\"PENDING\"}",
              ToolExecutionOutcome.PENDING,
              1
          ),
          new DeadlineExecutor()
      );

      ReActResult result = runtime.run(request(), budget(3, 1));

      assertThat(result.response().content()).contains("时间复杂度");
      assertThat(result.toolExecutions().getFirst().outcome())
          .isEqualTo(ToolExecutionOutcome.PENDING);
      assertThat(modelSteps).hasValue(2);
    }

    @Test
    @DisplayName("相同工具和参数重复调用时只执行一次")
    void shouldRejectDuplicateToolCall() {
      AtomicInteger modelSteps = new AtomicInteger();
      AtomicInteger executions = new AtomicInteger();
      AgentModelGateway model = context -> switch (modelSteps.getAndIncrement()) {
        case 0, 1 -> toolCall("question_bank_search", "redis");
        default -> {
          assertThat(context.observations()).hasSize(2);
          assertThat(context.observations().get(1).accepted()).isFalse();
          yield RespondAction.ask("换一个方向？", "重复工具调用已被拒绝");
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(model, (request, action) -> {
        executions.incrementAndGet();
        return execution(action);
      }, new DeadlineExecutor());

      ReActResult result = runtime.run(request(), budget(3, 2));

      assertThat(executions).hasValue(1);
      assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    @DisplayName("工具预算耗尽时拒绝本次调用并让模型直接给出最终回复")
    void shouldDemandFinalReplyWhenToolBudgetExhausted() {
      AtomicInteger modelSteps = new AtomicInteger();
      AtomicInteger executions = new AtomicInteger();
      AgentModelGateway model = context -> switch (modelSteps.getAndIncrement()) {
        case 0 -> toolCall("question_bank_search", "redis");
        case 1 -> toolCall("question_bank_search", "kafka");
        default -> {
          ToolObservation rejected = context.observations().get(1);
          assertThat(rejected.accepted()).isFalse();
          assertThat(rejected.output()).contains("预算", "最终回复");
          yield RespondAction.ask("聊聊 Redis 缓存穿透？", "工具预算已耗尽，直接提问");
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(model, (request, action) -> {
        executions.incrementAndGet();
        return execution(action);
      }, new DeadlineExecutor());

      ReActResult result = runtime.run(request(), budget(3, 1));

      assertThat(result.response().content()).isEqualTo("聊聊 Redis 缓存穿透？");
      assertThat(executions).hasValue(1);
      assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    @DisplayName("步预算耗尽时给模型一次直接回复的机会")
    void shouldGrantFinalReplyStepWhenStepBudgetExhausted() {
      AtomicInteger modelSteps = new AtomicInteger();
      AgentModelGateway model = context -> switch (modelSteps.getAndIncrement()) {
        case 0 -> toolCall("question_bank_search", "redis");
        case 1 -> toolCall("question_bank_search", "kafka");
        default -> {
          ToolObservation rejected = context.observations().get(1);
          assertThat(rejected.accepted()).isFalse();
          assertThat(rejected.output()).contains("预算", "最终回复");
          yield RespondAction.finish("面试结束", "步预算耗尽后直接收尾");
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      ReActResult result = runtime.run(request(), budget(2, 5));

      assertThat(result.response().content()).isEqualTo("面试结束");
      assertThat(result.toolExecutions()).hasSize(1);
      assertThat(modelSteps).hasValue(3);
    }

    @Test
    @DisplayName("模型在收到预算耗尽通知后仍坚持调用工具时快速失败")
    void shouldFailWhenModelInsistsOnToolCallAfterBudgetExhausted() {
      AtomicInteger modelSteps = new AtomicInteger();
      AgentModelGateway model = context -> switch (modelSteps.getAndIncrement()) {
        case 0 -> toolCall("question_bank_search", "redis");
        case 1 -> toolCall("question_bank_search", "kafka");
        default -> toolCall("question_bank_search", "mysql");
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      assertThatThrownBy(() -> runtime.run(request(), budget(3, 1)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("仍坚持调用工具");
    }

    @Test
    @DisplayName("模型持续重复调用同一工具时由步预算快速失败")
    void shouldStopAtStepBudget() {
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          context -> toolCall("question_bank_search", "redis"),
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      assertThatThrownBy(() -> runtime.run(request(), budget(2, 1)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("预算");
    }

    @Test
    @DisplayName("模型调用超过 deadline 时中断并失败")
    void shouldStopAtDeadline() {
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          context -> {
            try {
              Thread.sleep(5_000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError("模型调用应被 deadline 中断", e);
            }
            return RespondAction.finish("结束", "不应返回");
          },
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      assertThatThrownBy(() -> runtime.run(
          request(),
          new ReActBudget(2, 1, Duration.ofMillis(30))
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("超时");
    }

    @Test
    @DisplayName("流式运行时在工具调用后的最终回复步骤继续推送增量")
    void shouldStreamFinalResponseAfterToolCall() {
      AtomicInteger streamingCalls = new AtomicInteger();
      AtomicInteger plainCalls = new AtomicInteger();
      List<String> streamedDeltas = new ArrayList<>();
      AgentModelGateway model = new AgentModelGateway() {
        @Override
        public AgentAction nextAction(ReActModelContext context) {
          plainCalls.incrementAndGet();
          return RespondAction.ask("Redis 失效策略有哪些取舍？", "工具已返回审核题");
        }

        @Override
        public AgentAction nextActionStreaming(
            ReActModelContext context,
            Consumer<String> deltaSink
        ) {
          if (streamingCalls.getAndIncrement() == 0) {
            return toolCall("question_bank_search", "redis");
          }
          deltaSink.accept("Redis 失效策略");
          deltaSink.accept("有哪些取舍？");
          return RespondAction.ask("Redis 失效策略有哪些取舍？", "工具已返回审核题");
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      ReActResult result = runtime.runStreaming(request(), budget(3, 1), streamedDeltas::add);

      assertThat(streamingCalls).hasValue(2);
      assertThat(plainCalls).hasValue(0);
      assertThat(streamedDeltas).containsExactly("Redis 失效策略", "有哪些取舍？");
      assertThat(result.response().content()).isEqualTo("Redis 失效策略有哪些取舍？");
      assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    @DisplayName("deltaSink 为 null 时所有步骤都走非流式决策")
    void shouldNeverStreamWhenDeltaSinkIsNull() {
      AtomicInteger streamingCalls = new AtomicInteger();
      AtomicInteger plainCalls = new AtomicInteger();
      AgentModelGateway model = new AgentModelGateway() {
        @Override
        public AgentAction nextAction(ReActModelContext context) {
          return plainCalls.incrementAndGet() == 1
              ? toolCall("question_bank_search", "redis")
              : RespondAction.ask("下一题？", "继续验证");
        }

        @Override
        public AgentAction nextActionStreaming(
            ReActModelContext context,
            Consumer<String> deltaSink
        ) {
          streamingCalls.incrementAndGet();
          return nextAction(context);
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          model,
          (request, action) -> execution(action),
          new DeadlineExecutor()
      );

      ReActResult result = runtime.runStreaming(request(), budget(3, 1), null);

      assertThat(streamingCalls).hasValue(0);
      assertThat(plainCalls).hasValue(2);
      assertThat(result.response().content()).isEqualTo("下一题？");
    }
  }

  private ReActRequest request() {
    return new ReActRequest(
        "session-1",
        AgentRole.INTERVIEWER,
        null,
        new InterviewerContext(
            "JD",
            "Resume",
            1,
            6,
            0,
            "专业基础",
            "缓存与并发",
            List.of("question_bank_search"),
            null,
            List.of(),
            new CandidateAnswer(1, "候选人回答"),
            plannedMemory(2),
            List.of(),
            null,
            null,
            null
        )
    );
  }

  private ReActBudget budget(int maxSteps, int maxToolCalls) {
    return new ReActBudget(maxSteps, maxToolCalls, Duration.ofSeconds(1));
  }

  private WorkingMemorySnapshot plannedMemory(int turnIndex) {
    return new WorkingMemorySnapshot(
        "session-1",
        turnIndex,
        new TopicKey("java-backend", "CACHE"),
        null,
        0,
        TurnTriggerType.PLANNED
    );
  }

  private AgentAction toolCall(String name, String query) {
    return new ToolCallAction(name, Map.of("query", query), "需要客观信息");
  }

  private ToolExecution execution(ToolCallAction action) {
    return new ToolExecution(
        "invocation-1",
        action.toolName(),
        action.reason(),
        AgentRole.INTERVIEWER.name(),
        2,
        "keys=[query]",
        "matchedQuestionIds=[42]",
        "question:42",
        "{\"id\":42}",
        ToolExecutionOutcome.COMPLETED,
        1
    );
  }
}
