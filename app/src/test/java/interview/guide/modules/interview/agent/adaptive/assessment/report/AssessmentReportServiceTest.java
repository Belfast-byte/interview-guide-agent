package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.assessment.evidence.EvidenceType;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentReportServiceTest {

  @Test
  @DisplayName("候选人与企业报告投影同一组确定性结论和原文证据")
  void shouldProjectSameConclusionsForBothViews() {
    AssessmentReportFacts facts = completedFacts();
    AssessmentReportService service = new AssessmentReportService(
        new StubFactsSource(facts)
    );

    CandidateAssessmentReport candidate = service.candidateReport("session-1");
    EnterpriseAssessmentReport enterprise = service.enterpriseReport(
        "tenant-a",
        "session-1"
    );

    assertThat(candidate.dimensions()).isEqualTo(enterprise.dimensionMatrix());
    assertThat(candidate.dimensions().getFirst())
        .extracting(
            ReportDimensionConclusion::depthLevel,
            ReportDimensionConclusion::rationale
        )
        .containsExactly(DepthLevel.L3, "比较了方案边界");
    assertThat(candidate.dimensions().getFirst().evidences().getFirst())
        .extracting(
            ReportEvidenceReference::turnIndex,
            ReportEvidenceReference::question,
            ReportEvidenceReference::answer,
            ReportEvidenceReference::quote
        )
        .containsExactly(2, "如何取舍？", "原始回答：我比较了成本和一致性", "比较了成本和一致性");
    assertThat(enterprise.disclaimer())
        .isEqualTo("AI 初筛建议，不构成录用决定");
  }

  @Test
  @DisplayName("最低维度只定位下一层缺失能力且不生成综合分")
  void shouldLocateWeakestDimensionByNextDepthCapability() {
    AssessmentReportService service = new AssessmentReportService(
        new StubFactsSource(completedFacts())
    );

    CandidateAssessmentReport report = service.candidateReport("session-1");

    assertThat(report.weakPoints()).containsExactly(new CandidateWeakPoint(
        "问题解决",
        DepthLevel.L1,
        DepthLevel.L2,
        DepthLevel.L2.typicalPerformance()
    ));
  }

  @Test
  @DisplayName("未完成会话不生成报告")
  void shouldRejectIncompleteInterview() {
    AssessmentReportFacts facts = new AssessmentReportFacts(
        "session-1",
        "candidate-1",
        AdaptiveSessionStatus.IN_PROGRESS,
        completedFacts().dimensions(),
        List.of(),
        List.of()
    );
    AssessmentReportService service = new AssessmentReportService(
        new StubFactsSource(facts)
    );

    assertThatThrownBy(() -> service.candidateReport("session-1"))
        .hasFieldOrPropertyWithValue("code", 3007);
  }

  private AssessmentReportFacts completedFacts() {
    return new AssessmentReportFacts(
        "session-1",
        "candidate-1",
        AdaptiveSessionStatus.COMPLETED,
        List.of(
            new AssessmentReportDimensionFacts(
                0,
                "架构设计",
                "方案权衡",
                List.of(
                    assessment(1, DepthLevel.L2, "描述了应用", "做过缓存"),
                    assessment(
                        2,
                        DepthLevel.L3,
                        "比较了方案边界",
                        "比较了成本和一致性"
                    )
                )
            ),
            new AssessmentReportDimensionFacts(
                1,
                "问题解决",
                "定位过程",
                List.of(assessment(3, DepthLevel.L1, "复述了步骤", "先看日志"))
            )
        ),
        List.of(),
        List.of()
    );
  }

  private AssessmentReportTurnFacts assessment(
      int turnIndex,
      DepthLevel level,
      String rationale,
      String quote
  ) {
    String question = turnIndex == 2 ? "如何取舍？" : "请说明";
    String answer = turnIndex == 2
        ? "原始回答：我比较了成本和一致性"
        : "原始回答：" + quote;
    return new AssessmentReportTurnFacts(
        turnIndex,
        level,
        0.8,
        rationale,
        List.of(new AssessmentReportEvidenceFacts(
            EvidenceType.QUOTE,
            turnIndex,
            question,
            answer,
            quote,
            null,
            null,
            null,
            null
        ))
    );
  }

  private record StubFactsSource(AssessmentReportFacts facts)
      implements AssessmentReportFactsSource {

    @Override
    public AssessmentReportFacts loadCandidate(String sessionId) {
      return facts;
    }

    @Override
    public AssessmentReportFacts loadEnterprise(
        String tenantId,
        String sessionId
    ) {
      return facts;
    }
  }
}
