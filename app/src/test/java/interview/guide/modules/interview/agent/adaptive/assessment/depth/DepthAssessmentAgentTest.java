package interview.guide.modules.interview.agent.adaptive.assessment.depth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.core.context.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.context.ProbeGap;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
        .containsExactly("dimension", "focus", "question", "answer", "rubric")
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
  @DisplayName("评估决策透传锚定原文的追问点")
  void shouldCarryProbeGapsIntoDecision() {
    ProbeGap gap = new ProbeGap("版本号", "未说明版本号如何推进");
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(
            DepthLevel.L2,
            0.8,
            "描述了应用",
            List.of("重要数据使用版本号"),
            List.of(gap)
        )
    );

    AssessmentDecision decision = agent.assess(request(), null);

    assertThat(decision.probeGaps()).containsExactly(gap);
  }

  @Test
  @DisplayName("追问点锚定内容不在回答原文时明确失败且不静默重调模型")
  void shouldRejectMissingProbeGapAnchor() {
    AtomicInteger calls = new AtomicInteger();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      calls.incrementAndGet();
      return new AssessmentProposal(
          DepthLevel.L2,
          0.8,
          "描述了应用",
          List.of("重要数据使用版本号"),
          List.of(new ProbeGap("布隆过滤器", "未说明误判率"))
      );
    });

    assertThatThrownBy(() -> agent.assess(request(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("锚定内容不存在");

    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("模型超时等非校验类失败不重试，直接抛出")
  void shouldNotRetryWhenModelTimesOut() {
    AtomicInteger calls = new AtomicInteger();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      calls.incrementAndGet();
      throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT, "评估模型超时");
    });

    assertThatThrownBy(() -> agent.assess(request(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("评估模型超时");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("结果结构性不完整时直接失败")
  void shouldRejectStructuralIncompleteness() {
    AtomicInteger calls = new AtomicInteger();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      calls.incrementAndGet();
      return new AssessmentProposal(
          null,
          0.8,
          "描述了应用",
          List.of("重要数据使用版本号")
      );
    });

    assertThatThrownBy(() -> agent.assess(request(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不完整");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("追问点数量不由 Java 截断")
  void shouldKeepModelProbeGaps() {
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(
            DepthLevel.L2,
            0.8,
            "描述了应用",
            List.of("重要数据使用版本号"),
            List.of(
                new ProbeGap("版本号", "未说明版本号如何推进"),
                new ProbeGap("延迟双删", "未说明延迟窗口"),
                new ProbeGap("概率", "未说明残余风险")
            )
        )
    );

    AssessmentDecision decision = agent.assess(request(), null);

    assertThat(decision.probeGaps()).containsExactly(
        new ProbeGap("版本号", "未说明版本号如何推进"),
        new ProbeGap("延迟双删", "未说明延迟窗口"),
        new ProbeGap("概率", "未说明残余风险")
    );
  }

  @Test
  @DisplayName("追问点锚点经全半角和空白归一化后命中回答原文")
  void shouldMatchProbeGapAnchorAfterNormalization() {
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(
            DepthLevel.L2,
            0.8,
            "描述了应用",
            List.of("缓存使用 Redis 做延迟双删"),
            List.of(new ProbeGap("缓存使用 Ｒｅｄｉｓ 做延迟双删", "未说明延迟窗口"))
        )
    );
    AssessmentRequest request = new AssessmentRequest(
        "session-1",
        1,
        AssessmentContext.currentAnswer(
            "专业基础",
            "缓存一致性",
            "如何保证缓存一致性？",
            "缓存使用  Redis  做延迟双删。"
        )
    );

    AssessmentDecision decision = agent.assess(request, null);

    assertThat(decision.probeGaps())
        .containsExactly(new ProbeGap("缓存使用 Ｒｅｄｉｓ 做延迟双删", "未说明延迟窗口"));
  }

  @Test
  @DisplayName("L0 无证据评级允许证据引用为空")
  void shouldAllowEmptyEvidenceQuotesForL0() {
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) ->
        new AssessmentProposal(
            DepthLevel.L0,
            0.9,
            "答非所问",
            List.of()
        )
    );

    AssessmentDecision decision = agent.assess(request(), null);

    assertThat(decision.depthLevel()).isEqualTo(DepthLevel.L0);
    assertThat(decision.evidenceQuotes()).isEmpty();
  }

  @Test
  @DisplayName("评估模型返回非有限置信度时直接失败")
  void shouldRejectNonFiniteConfidence() {
    AtomicInteger calls = new AtomicInteger();
    DepthAssessmentAgent agent = new DepthAssessmentAgent((request, provider) -> {
      calls.incrementAndGet();
      return new AssessmentProposal(
          DepthLevel.L2,
          Double.NaN,
          "描述了应用",
          List.of("使用 Redis")
      );
    });

    assertThatThrownBy(() -> agent.assess(request(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不完整");
    assertThat(calls.get()).isEqualTo(1);
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
