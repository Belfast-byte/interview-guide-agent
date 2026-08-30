package interview.guide.modules.interview.agent.adaptive.application;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testDimension;
import static org.assertj.core.api.Assertions.assertThat;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.AssessmentDecision;
import interview.guide.modules.interview.agent.adaptive.core.context.CoverageView;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import interview.guide.modules.interview.agent.adaptive.planning.DimensionProposal;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import interview.guide.modules.interview.agent.adaptive.runtime.DecisionObservation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TargetBudgetPolicyTest {

  private final TargetBudgetPolicy policy = new TargetBudgetPolicy();

  @Test
  @DisplayName("两轮预算第三次回答仍有 Gap 时生成预算耗尽 Observation")
  void shouldObserveExhaustedBudget() {
    var decision = policy.evaluate(coverage(3), assessment(true));

    assertThat(decision.exhausted()).isTrue();
    assertThat(decision.observations()).singleElement().satisfies(observation -> {
      assertThat(observation.kind()).isEqualTo(DecisionObservation.Kind.BUDGET_EXHAUSTED);
      assertThat(observation.data()).containsEntry("targetId", "target-0");
    });
  }

  @Test
  @DisplayName("未达到预算或当前评估无 Gap 时不生成 Observation")
  void shouldKeepTargetOpenWithoutExhaustion() {
    assertThat(policy.evaluate(coverage(2), assessment(true)).exhausted()).isFalse();
    assertThat(policy.evaluate(coverage(3), assessment(false)).exhausted()).isFalse();
  }

  private CoverageView coverage(int askedTurns) {
    PlannedDimension dimension = dimension();
    return new CoverageView(
        askedTurns,
        3,
        List.of(new CoverageView.TargetCoverage(
            "target-0", dimension.target(), askedTurns, DepthLevel.L1,
            List.of(), List.of())),
        List.of(),
        List.of()
    );
  }

  private AnswerAssessment assessment(boolean withGap) {
    return new AnswerAssessment(
        dimension(),
        new AssessmentDecision(
            "session-1", 3, DepthLevel.L1, 0.8, "仍缺少边界说明", List.of("工具"),
            withGap ? List.of(new ProbeGap("工具", "未说明失败边界")) : List.of()
        ),
        List.of()
    );
  }

  private PlannedDimension dimension() {
    return testDimension(new DimensionProposal(
        "工具设计", "工具调用边界", "LLM_CALLING", 2, "ai-agent-dev"), 0, 0);
  }
}
