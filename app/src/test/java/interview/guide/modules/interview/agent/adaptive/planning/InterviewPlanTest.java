package interview.guide.modules.interview.agent.adaptive.planning;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.TopicKey;
import interview.guide.modules.interview.agent.adaptive.core.session.CandidateLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.InterviewSessionSettings;
import interview.guide.modules.interview.agent.adaptive.core.session.PracticeScope;
import interview.guide.modules.interview.agent.adaptive.core.session.SessionMode;
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
      InterviewPlan plan = testPlan("session-1", proposal(3));

      assertThat(plan.maxTurns()).isEqualTo(6);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(2, 2, 2);
    }

    @Test
    @DisplayName("七个维度不突破十二轮且每个维度至少一轮")
    void shouldCapBudgetAndCoverEveryDimension() {
      InterviewPlan plan = testPlan("session-1", proposal(7));

      assertThat(plan.maxTurns()).isEqualTo(12);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(2, 2, 2, 2, 2, 1, 1);
    }

    @Test
    @DisplayName("模型建议影响维度预算但不改变总轮次和覆盖保底")
    void shouldNormalizeSuggestedTurnsWithinHardBudget() {
      InterviewPlan plan = testPlan(
          "session-1",
          new PlanProposal(List.of(
              new DimensionProposal(
                  "核心项目", "架构取舍", "PROJECT", 12, "java-backend"
              ),
              new DimensionProposal(
                  "专业基础", "并发", "JAVA", 1, "java-backend"
              ),
              new DimensionProposal(
                  "协作", "复盘", "TEAMWORK", 1, "java-backend"
              )
          ))
      );

      assertThat(plan.maxTurns()).isEqualTo(6);
      assertThat(plan.dimensions()).extracting(PlannedDimension::allocatedTurns)
          .containsExactly(4, 1, 1);
    }

    @Test
    @DisplayName("候选人阶段由代码裁决目标深度")
    void shouldDecideDepthFromCandidateLevel() {
      InterviewSessionSettings settings = new InterviewSessionSettings(
          SessionMode.EVALUATION,
          CandidateLevel.EXPERIENCED,
          PracticeScope.none()
      );

      PlannedDimension target = InterviewPlan.decide("session-1", proposal(1), settings)
          .dimensions().getFirst();

      assertThat(target.expectedDepth()).isEqualTo(DepthLevel.L3);
      assertThat(target.depthCeiling()).isEqualTo(DepthLevel.L4);
      assertThat(target.evidenceObjectives()).hasSize(1);
    }

    @Test
    @DisplayName("练习计划不能越过用户指定主题")
    void shouldRejectPracticePlanOutsideExplicitScope() {
      InterviewSessionSettings settings = new InterviewSessionSettings(
          SessionMode.PRACTICE,
          CandidateLevel.CAMPUS,
          new PracticeScope(List.of(new TopicKey("java-backend", "REDIS")))
      );

      assertThatThrownBy(() -> InterviewPlan.decide("session-1", proposal(1), settings))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("范围外主题");
    }

    @Test
    @DisplayName("重复维度被确定性规则拒绝")
    void shouldRejectDuplicateDimension() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("专业基础"),
          dimension(" 专业基础 ")
      ));

      assertThatThrownBy(() -> testPlan("session-1", proposal))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("重复维度");
    }

    @Test
    @DisplayName("同一计划内重复 TopicKey 被拒绝")
    void shouldRejectDuplicateTopicKey() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("专业基础", "java-backend", "REDIS"),
          dimension("项目实践", "java-backend", "REDIS")
      ));

      assertThatThrownBy(() -> testPlan("session-1", proposal))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("重复主题");
    }

    @Test
    @DisplayName("不同 Skill 下相同 focusId 是不同主题")
    void shouldAllowSameFocusAcrossSkills() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("后端缓存", "java-backend", "REDIS"),
          dimension("系统缓存", "system-design", "REDIS")
      ));

      assertThat(testPlan("session-1", proposal).dimensions()).hasSize(2);
    }

    @Test
    @DisplayName("超过持久化边界的维度名称被拒绝")
    void shouldRejectOversizedDimensionName() {
      PlanProposal proposal = new PlanProposal(List.of(
          dimension("维".repeat(101))
      ));

      assertThatThrownBy(() -> testPlan("session-1", proposal))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("长度限制");
    }
  }

  private PlanProposal proposal(int count) {
    return new PlanProposal(java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> dimension(
            "维度-" + index,
            "java-backend",
            "JAVA_" + index
        ))
        .toList());
  }

  private DimensionProposal dimension(String name) {
    return dimension(name, "java-backend", "JAVA");
  }

  private DimensionProposal dimension(String name, String skillId, String focusId) {
    return new DimensionProposal(name, name + "重点", focusId, 12, skillId);
  }
}
