package interview.guide.modules.interview.agent.adaptive.assessment;

import interview.guide.common.exception.BusinessException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepthAssessmentAgentTest {

  @Test
  @DisplayName("评估 Agent 只接收当前问题回答和本维度量规")
  void shouldKeepAssessmentContextIsolated() {
    assertThat(Arrays.stream(AssessmentContext.class.getRecordComponents())
        .map(RecordComponent::getName))
        .containsExactly("dimension", "focus", "question", "answer", "toolResult", "rubric")
        .doesNotContain(
            "candidateId",
            "jd",
            "resume",
            "history",
            "coveredTopics",
            "unverifiedClaims"
        );
  }

  @Test
  @DisplayName("评估 Agent 返回结构化深度结论并保留逐字证据候选")
  void shouldReturnStructuredAssessment() {
    AtomicReference<AssessmentRequest> captured = new AtomicReference<>();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      captured.set(request);
      return new AssessmentProposal(
          DepthLevel.L3,
          0.85,
          " 能说明方案代价和边界 ",
          false,
          List.of("延迟双删只能降低概率")
      );
    });
    AssessmentRequest request = request();

    AssessmentDecision decision = agent.assess(request, "provider-1");

    assertThat(captured.get()).isSameAs(request);
    assertThat(decision.depthLevel()).isEqualTo(DepthLevel.L3);
    assertThat(decision.confidence()).isEqualTo(0.85);
    assertThat(decision.rationaleSummary()).isEqualTo("能说明方案代价和边界");
    assertThat(decision.evidenceQuotes())
        .containsExactly("延迟双删只能降低概率");
  }

  @Test
  @DisplayName("评估模型返回非有限置信度时快速失败")
  void shouldRejectNonFiniteConfidence() {
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(
            DepthLevel.L2,
            Double.NaN,
            "描述了应用",
            false,
            List.of("使用 Redis")
        )
    );

    assertThatThrownBy(() -> agent.assess(request(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不完整");
  }

  private AssessmentRequest request() {
    return new AssessmentRequest(
        "session-1",
        1,
        AssessmentContext.currentAnswer(
            "专业基础",
            "缓存一致性",
            "如何保证缓存一致性？",
            "延迟双删只能降低概率，重要数据要使用版本号。"
        )
    );
  }
}
