package interview.guide.modules.interview.agent.adaptive.planning;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewPlanTest {

  @Nested
  @DisplayName("规划裁决")
  class Decision {

    @Test
    @DisplayName("三个维度由代码分配六轮且保持优先级顺序")
    void shouldAllocateTwoTurnsPerDimension() {
      InterviewPlan plan = InterviewPlan.decide("session-1", proposal(3));

      assertThat(plan.maxTurns()).isEqualTo(6);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(2, 2, 2);
      assertThat(plan.dimensionForTurn(1).order()).isZero();
      assertThat(plan.dimensionForTurn(3).order()).isEqualTo(1);
      assertThat(plan.dimensionForTurn(5).order()).isEqualTo(2);
    }

    @Test
    @DisplayName("七个维度不突破十二轮且每个维度至少一轮")
    void shouldCapBudgetAndCoverEveryDimension() {
      InterviewPlan plan = InterviewPlan.decide("session-1", proposal(7));

      assertThat(plan.maxTurns()).isEqualTo(12);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(2, 2, 2, 2, 2, 1, 1);
      assertThat(plan.dimensionForTurn(12).order()).isEqualTo(6);
    }

    @Test
    @DisplayName("模型建议影响维度预算但不改变总轮次和覆盖保底")
    void shouldNormalizeSuggestedTurnsWithinHardBudget() {
      InterviewPlan plan = InterviewPlan.decide(
          "session-1",
          new PlanProposal(List.of(
              new DimensionProposal(
                  "核心项目", "架构取舍", "PROJECT", 12, List.of(), "java-backend"
              ),
              new DimensionProposal(
                  "专业基础", "并发", "JAVA", 1, List.of(), "java-backend"
              ),
              new DimensionProposal(
                  "协作", "复盘", "TEAMWORK", 1, List.of(), "java-backend"
              )
          ))
      );

      assertThat(plan.maxTurns()).isEqualTo(6);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(4, 1, 1);
    }

    @Test
    @DisplayName("重复维度被确定性规则拒绝")
    void shouldRejectDuplicateDimension() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("专业基础"),
          dimension(" 专业基础 ")
      ));

      assertThatThrownBy(() -> InterviewPlan.decide("session-1", proposal))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("重复维度");
    }

    @Test
    @DisplayName("超过持久化边界的维度名称被拒绝")
    void shouldRejectOversizedDimensionName() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("维".repeat(101))
      ));

      assertThatThrownBy(() -> InterviewPlan.decide("session-1", proposal))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("长度限制");
    }
  }

  @Test
  @DisplayName("维度预算完成后由代码切换到下一维度")
  void shouldAdvanceDimensionAfterAllocatedTurns() {
    InterviewPlan plan = InterviewPlan.decide("session-1", proposal(2));

    plan = plan.answer(1);
    assertThat(plan.dimensions()).extracting(PlannedDimension::status)
        .containsExactly(PlanDimensionStatus.IN_PROGRESS, PlanDimensionStatus.PENDING);

    plan = plan.answer(2);
    assertThat(plan.dimensions()).extracting(PlannedDimension::status)
        .containsExactly(PlanDimensionStatus.COMPLETED, PlanDimensionStatus.IN_PROGRESS);

    plan = plan.answer(3).answer(4);
    assertThat(plan.dimensions()).extracting(PlannedDimension::status)
        .containsOnly(PlanDimensionStatus.COMPLETED);
  }

  private PlanProposal proposal(int count) {
    return new PlanProposal(java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> dimension("维度-" + index))
        .toList());
  }

  private DimensionProposal dimension(String name) {
    return new DimensionProposal(name, name + "重点", "JAVA", 12, List.of(), "java-backend");
  }
}
