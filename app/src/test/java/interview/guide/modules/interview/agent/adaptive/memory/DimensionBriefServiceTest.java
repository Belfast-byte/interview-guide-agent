package interview.guide.modules.interview.agent.adaptive.memory;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.core.AdaptiveInterviewTurn;
import interview.guide.modules.interview.agent.adaptive.core.AgentResponseType;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.DimensionBrief;
import interview.guide.modules.interview.agent.adaptive.planning.PlanDimensionStatus;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedDimension;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DimensionBriefServiceTest {

  @Test
  @DisplayName("生成小结时使用当前回答并保留合法轮次引用")
  void shouldGenerateBriefFromCompleteDimensionFacts() {
    AtomicReference<DimensionBriefRequest> captured = new AtomicReference<>();
    DimensionBriefService service = new DimensionBriefService((request, provider) -> {
      captured.set(request);
      return new DimensionBriefProposal("讨论了缓存一致性的方案与取舍", List.of(1, 2));
    });

    DimensionBrief brief = service.summarize(
        "session-1",
        dimension(),
        List.of(turn(1, 0, "回答一"), turn(2, 0, null)),
        new CandidateAnswer(2, "当前提交的完整回答"),
        "provider-1"
    );

    assertThat(captured.get().turns()).extracting(DimensionBriefTurn::answer)
        .containsExactly("回答一", "当前提交的完整回答");
    assertThat(brief.turnIndexes()).containsExactly(1, 2);
    assertThat(brief.keyFindings()).isEqualTo("讨论了缓存一致性的方案与取舍");
  }

  @Test
  @DisplayName("小结引用其他维度轮次时快速失败")
  void shouldRejectTurnReferenceOutsideDimension() {
    DimensionBriefService service = new DimensionBriefService((request, provider) ->
        new DimensionBriefProposal("非法引用", List.of(1, 3))
    );

    assertThatThrownBy(() -> service.summarize(
        "session-1",
        dimension(),
        List.of(turn(1, 0, "回答一"), turn(2, 0, null), turn(3, 1, "其他维度")),
        new CandidateAnswer(2, "回答二"),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("非法轮次");
  }

  @Test
  @DisplayName("小结包含重复轮次引用时快速失败")
  void shouldRejectDuplicateTurnReferences() {
    DimensionBriefService service = new DimensionBriefService((request, provider) ->
        new DimensionBriefProposal("重复引用", List.of(1, 1))
    );

    assertThatThrownBy(() -> service.summarize(
        "session-1",
        dimension(),
        List.of(turn(1, 0, "回答一"), turn(2, 0, null)),
        new CandidateAnswer(2, "回答二"),
        null
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("非法轮次");
  }

  private PlannedDimension dimension() {
    return new PlannedDimension(
        0,
        "专业基础",
        "缓存一致性",
        2,
        List.of(),
        null,
        2,
        1,
        PlanDimensionStatus.IN_PROGRESS
    );
  }

  private AdaptiveInterviewTurn turn(
      int turnIndex,
      int dimensionOrder,
      String answer
  ) {
    return new AdaptiveInterviewTurn(
        turnIndex,
        dimensionOrder,
        "问题 " + turnIndex,
        "提问原因",
        answer,
        AgentResponseType.ASK,
        "下一步",
        "决策原因"
    );
  }
}
