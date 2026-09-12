package interview.guide.modules.interview.agent.adaptive.planning;

import static interview.guide.modules.interview.agent.adaptive.support.AdaptiveTestFixtures.testPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.runtime.AgentDecision;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InitialQuestionProposalTest {

  @Test
  @DisplayName("创建 Agent 的首题选择映射为 Plan 内 Target 和初始 Working Memory")
  void shouldMapInitialQuestionToDecision() {
    InterviewPlan plan = plan();
    InitialQuestionProposal proposal = new InitialQuestionProposal(
        1,
        "请说明一次线上缓存一致性问题的定位过程。",
        "优先验证岗位核心实践",
        "验证问题定位与取舍", List.of()
    );

    AgentDecision decision = proposal.toDecision(plan, List.of());

    assertThat(decision.workingMemory().focus().activeTargetId()).isEqualTo("target-1");
    assertThat(decision.workingMemory().deliberation().nextProbeIntent())
        .isEqualTo("验证问题定位与取舍");
    assertThat(decision.action()).isEqualTo(new AgentDecision.Ask(
        "target-1",
        null,
        new AgentDecision.QuestionDraft(
            "请说明一次线上缓存一致性问题的定位过程。",
            "优先验证岗位核心实践",
            List.of()
        )
    ));
  }

  @Test
  @DisplayName("创建 Agent 选择 Plan 外 Target 时明确失败")
  void shouldRejectTargetOutsidePlan() {
    InitialQuestionProposal proposal = new InitialQuestionProposal(
        2, "问题", "理由", "意图", List.of());

    assertThatThrownBy(() -> proposal.toDecision(plan(), List.of()))
        .isInstanceOf(BusinessException.class)
        .hasMessage("首题 Target 不属于 Plan");
  }

  private InterviewPlan plan() {
    return testPlan("session-1", new PlanProposal(List.of(
        dimension("Java 基础", "并发模型", "java"),
        dimension("项目经验", "缓存一致性", "cache")
    )));
  }

  @Test
  void storesUsedEpisodeInInitialWorkingMemory() {
    var proposal = new InitialQuestionProposal(0, "换库存场景解释锁竞争", "参考上次不足",
        "验证底层机制", List.of("episode:7"));
    var decision = proposal.toDecision(plan(), List.of("episode:7"));
    assertThat(decision.workingMemory().deliberation().adoptedObservationRefs())
        .containsExactly("episode:7");
    assertThat(decision.workingMemory().focus().activeGapId()).isNull();
    assertThatThrownBy(() -> proposal.toDecision(plan(), List.of()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Episode 引用");
  }

  private DimensionProposal dimension(String name, String focus, String focusId) {
    return new DimensionProposal(name, focus, focusId, 1, "java-backend");
  }
}
