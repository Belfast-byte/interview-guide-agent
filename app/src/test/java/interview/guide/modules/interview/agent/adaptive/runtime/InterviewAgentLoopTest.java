package interview.guide.modules.interview.agent.adaptive.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.AgentContext;
import interview.guide.modules.interview.agent.adaptive.core.context.CapabilityTarget;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.MemoryOwner;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemory;
import interview.guide.modules.interview.agent.adaptive.core.context.WorkingMemoryValidator;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterviewAgentLoopTest {

  @Test
  @DisplayName("模型可选择非首 Target 的 Gap 且不受 expectedDepth 策略限制")
  void shouldAcceptModelSelectedTargetAndGap() {
    AgentDecision expected = ask("target-1", 12L, memory("target-1", 12L));
    InterviewAgentLoop loop = loop(context -> expected);

    AgentDecision actual = loop.run(context(), Duration.ofSeconds(1));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("非法 Target 和伪造引用作为结构化 Observation 返回模型后允许重新决策")
  void shouldReturnRejectionToModel() {
    AtomicInteger calls = new AtomicInteger();
    List<DecisionModelContext> requests = new ArrayList<>();
    InterviewAgentLoop loop = loop(context -> {
      requests.add(context);
      return switch (calls.getAndIncrement()) {
        case 0 -> ask("missing-target", null, memory("target-0", null));
        case 1 -> askWithSourceRef("forged-observation");
        default -> ask("target-1", 12L, memory("target-1", 12L));
      };
    });

    AgentDecision decision = loop.run(context(), Duration.ofSeconds(1));

    assertThat(decision.action()).isInstanceOf(AgentDecision.Ask.class);
    assertThat(requests).hasSize(3);
    assertThat(requests.get(1).observations()).containsExactly(
        new DecisionObservation(
            "validation-0",
            DecisionObservation.Kind.VALIDATION_REJECTION,
            "action.ask.targetId",
            "Target 不属于当前 Plan",
            null,
            java.util.Map.of(),
            List.of()
        )
    );
    assertThat(requests.get(2).observations().get(1))
        .isEqualTo(new DecisionObservation(
            "validation-1",
            DecisionObservation.Kind.VALIDATION_REJECTION,
            "action.ask.question.adoptedSourceRefs",
            "引用不在成功 Tool Observation 中",
            null,
            java.util.Map.of(),
            List.of()
        ));
  }

  @Test
  @DisplayName("模型 FINISH 决定原样结束循环")
  void shouldReturnFinishDecision() {
    AgentDecision expected = new AgentDecision(
        memory("target-1", 12L),
        new AgentDecision.Finish("已有信息足够形成结论")
    );

    assertThat(loop(context -> expected).run(context(), Duration.ofSeconds(1)))
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("预算 Observation 在第一次模型决策前直接注入")
  void shouldInjectInitialBudgetObservation() {
    List<DecisionModelContext> requests = new ArrayList<>();
    DecisionObservation observation = new DecisionObservation(
        "budget-exhausted-target-0",
        DecisionObservation.Kind.BUDGET_EXHAUSTED,
        "coverage.targets[target-0]",
        "请切换 Target",
        null,
        java.util.Map.of("targetId", "target-0"),
        List.of()
    );
    InterviewAgentLoop loop = loop(context -> {
      requests.add(context);
      return ask("target-1", 12L, memory("target-1", 12L));
    });

    loop.run(context(), List.of(observation), Duration.ofSeconds(1));

    assertThat(requests.getFirst().observations()).containsExactly(observation);
  }

  @Test
  @DisplayName("共享 deadline 耗尽时明确失败")
  void shouldFailWhenDeadlineExhausted() {
    InterviewAgentLoop loop = loop(context -> {
      throw new AssertionError("deadline 耗尽后不应调用模型");
    });

    assertThatThrownBy(() -> loop.run(context(), Duration.ZERO))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("超时");
  }

  private InterviewAgentLoop loop(InterviewDecisionModel model) {
    return new InterviewAgentLoop(
        model,
        new AgentDecisionValidator(new WorkingMemoryValidator()),
        batch -> List.of(),
        new DeadlineExecutor()
    );
  }

  private AgentDecision ask(String targetId, Long gapId, WorkingMemory memory) {
    return new AgentDecision(
        memory,
        new AgentDecision.Ask(
            targetId,
            gapId,
            new AgentDecision.QuestionDraft(
                "请具体说明并发更新时的冲突处理。",
                "验证候选人的并发边界理解",
                List.of()
            )
        )
    );
  }

  private AgentDecision askWithSourceRef(String sourceRef) {
    return new AgentDecision(
        memory("target-1", 12L),
        new AgentDecision.Ask(
            "target-1",
            12L,
            new AgentDecision.QuestionDraft("请展开说明。", "验证细节", List.of(sourceRef))
        )
    );
  }

  private WorkingMemory memory(String targetId, Long gapId) {
    return new WorkingMemory(
        null,
        new WorkingMemory.Focus(targetId, gapId, List.of()),
        new WorkingMemory.Deliberation(List.of(), "继续验证边界", List.of())
    );
  }

  private AgentContext context() {
    CapabilityTarget first = target(0, "target-0", DepthLevel.L2);
    CapabilityTarget second = target(1, "target-1", DepthLevel.L1);
    CoverageView coverage = new CoverageView(
        1,
        3,
        List.of(
            targetCoverage("target-0", first, List.of()),
            targetCoverage("target-1", second, List.of(12L))
        ),
        List.of(new CoverageView.OpenProbeGap(
            12L, 22L, "target-1", 1, "并发更新", "缺少冲突处理"
        )),
        List.of()
    );
    return new AgentContext(
        new AgentContext.SessionWindow(
            new AgentContext.SessionIdentity(
                "session-1", "provider-1", new MemoryOwner("tenant-1", "candidate-1")),
            SessionMode.EVALUATION,
            4
        ),
        new AgentContext.Facts(coverage, List.of(), List.of(), List.of()),
        memory("target-0", null)
    );
  }

  private CoverageView.TargetCoverage targetCoverage(
      String id,
      CapabilityTarget target,
      List<Long> gaps
  ) {
    return new CoverageView.TargetCoverage(id, target, 0, null, gaps, List.of());
  }

  private CapabilityTarget target(int order, String id, DepthLevel expected) {
    return new CapabilityTarget(
        new CapabilityTarget.Identity(order, id, "focus", new TopicKey("skill", id)),
        new CapabilityTarget.Budget(2, 2),
        new CapabilityTarget.Depth(expected, DepthLevel.L4),
        List.of()
    );
  }
}
