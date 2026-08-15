package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.InterviewerContext;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
import interview.guide.modules.interview.agent.adaptive.role.AgentRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
          (request, action) -> execution(action)
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
          (request, action) -> execution(action)
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
          )
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
      });

      ReActResult result = runtime.run(request(), budget(3, 2));

      assertThat(executions).hasValue(1);
      assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    @DisplayName("模型持续空转时由步预算快速失败")
    void shouldStopAtStepBudget() {
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          context -> toolCall("question_bank_search", "redis"),
          (request, action) -> execution(action)
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
          (request, action) -> execution(action)
      );

      assertThatThrownBy(() -> runtime.run(
          request(),
          new ReActBudget(2, 1, Duration.ofMillis(30))
      )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("超时");
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
            List.of()
        )
    );
  }

  private ReActBudget budget(int maxSteps, int maxToolCalls) {
    return new ReActBudget(maxSteps, maxToolCalls, Duration.ofSeconds(1));
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
        1
    );
  }
}
