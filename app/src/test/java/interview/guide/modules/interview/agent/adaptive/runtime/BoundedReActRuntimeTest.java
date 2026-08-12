package interview.guide.modules.interview.agent.adaptive.runtime;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.AgentAction;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.RespondAction;
import interview.guide.modules.interview.agent.adaptive.core.ToolCallAction;
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
          action -> "不应执行"
      );

      RespondAction actual = runtime.run(request(), budget(3, 1));

      assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("工具结果作为 observation 回到下一模型步")
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
            "题目-42"
        ));
        return RespondAction.ask("Redis 失效策略有哪些取舍？", "题库已返回审核题");
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(model, action -> "题目-42");

      RespondAction response = runtime.run(request(), budget(3, 1));

      assertThat(response.content()).isEqualTo("Redis 失效策略有哪些取舍？");
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
          yield RespondAction.ask("换一个方向。", "重复工具调用已被拒绝");
        }
      };
      BoundedReActRuntime runtime = new BoundedReActRuntime(model, action -> {
        executions.incrementAndGet();
        return "题目-42";
      });

      runtime.run(request(), budget(3, 2));

      assertThat(executions).hasValue(1);
    }

    @Test
    @DisplayName("模型持续空转时由步预算快速失败")
    void shouldStopAtStepBudget() {
      BoundedReActRuntime runtime = new BoundedReActRuntime(
          context -> toolCall("question_bank_search", "redis"),
          action -> "题目-42"
      );

      assertThatThrownBy(() -> runtime.run(request(), budget(2, 1)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("步预算");
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
          action -> "不应执行"
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
        null,
        "JD",
        "Resume",
        6,
        "专业基础",
        "缓存与并发",
        List.of(),
        new CandidateAnswer(1, "候选人回答")
    );
  }

  private ReActBudget budget(int maxSteps, int maxToolCalls) {
    return new ReActBudget(maxSteps, maxToolCalls, Duration.ofSeconds(1));
  }

  private AgentAction toolCall(String name, String query) {
    return new ToolCallAction(name, Map.of("query", query), "需要客观信息");
  }
}
