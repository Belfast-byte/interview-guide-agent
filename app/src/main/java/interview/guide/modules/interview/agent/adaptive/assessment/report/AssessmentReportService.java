package interview.guide.modules.interview.agent.adaptive.assessment.report;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.assessment.FinalAssessmentSelector;
import interview.guide.modules.interview.agent.adaptive.assessment.depth.DepthLevel;
import interview.guide.modules.interview.agent.adaptive.core.session.AdaptiveSessionStatus;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评估报告服务，基于持久化评估结果确定性组装候选人报告。
 */
@Service
public class AssessmentReportService {

  public static final String ENTERPRISE_DISCLAIMER =
      "AI 初筛建议，不构成录用决定";

  private static final Comparator<AssessmentReportTurnFacts> FINAL_ASSESSMENT =
      FinalAssessmentSelector.byDepthThenTurn(
          AssessmentReportTurnFacts::depthLevel,
          AssessmentReportTurnFacts::turnIndex
      );

  private final AssessmentReportFactsSource source;

  public AssessmentReportService(AssessmentReportFactsSource source) {
    this.source = source;
  }

  public CandidateAssessmentReport candidateReport(String sessionId) {
    AssessmentReportFacts facts = source.loadCandidate(sessionId);
    List<ReportDimensionConclusion> conclusions = conclusions(facts);
    return new CandidateAssessmentReport(
        facts.sessionId(),
        conclusions,
        weakPoints(conclusions),
        facts.practiceRecommendations(),
        facts.projectSources()
    );
  }

  public EnterpriseAssessmentReport enterpriseReport(
      String tenantId,
      String sessionId
  ) {
    AssessmentReportFacts facts = source.loadEnterprise(tenantId, sessionId);
    return new EnterpriseAssessmentReport(
        facts.sessionId(),
        facts.candidateId(),
        conclusions(facts),
        facts.projectSources(),
        ENTERPRISE_DISCLAIMER
    );
  }

  private List<ReportDimensionConclusion> conclusions(
      AssessmentReportFacts facts
  ) {
    if (facts.status() != AdaptiveSessionStatus.COMPLETED
        || facts.dimensions().stream()
            .anyMatch(dimension -> dimension.assessments().isEmpty())) {
      throw new BusinessException(
          ErrorCode.INTERVIEW_NOT_COMPLETED,
          "面试评估尚未完成"
      );
    }
    return facts.dimensions().stream()
        .map(this::conclusion)
        .toList();
  }

  private ReportDimensionConclusion conclusion(
      AssessmentReportDimensionFacts dimension
  ) {
    AssessmentReportTurnFacts finalAssessment = dimension.assessments().stream()
        .max(FINAL_ASSESSMENT)
        .orElseThrow();
    return new ReportDimensionConclusion(
        dimension.order(),
        dimension.dimension(),
        dimension.focus(),
        finalAssessment.depthLevel(),
        finalAssessment.confidence(),
        finalAssessment.rationale(),
        finalAssessment.evidences().stream()
            .map(ReportEvidenceReference::from)
            .toList()
    );
  }

  private List<CandidateWeakPoint> weakPoints(
      List<ReportDimensionConclusion> conclusions
  ) {
    DepthLevel weakestLevel = conclusions.stream()
        .map(ReportDimensionConclusion::depthLevel)
        .min(DepthLevel::compareTo)
        .orElseThrow();
    if (weakestLevel == DepthLevel.L4) {
      return List.of();
    }
    DepthLevel missingLevel = DepthLevel.values()[weakestLevel.ordinal() + 1];
    return conclusions.stream()
        .filter(conclusion -> conclusion.depthLevel() == weakestLevel)
        .map(conclusion -> new CandidateWeakPoint(
            conclusion.dimension(),
            weakestLevel,
            missingLevel,
            missingLevel.typicalPerformance()
        ))
        .toList();
  }
}
