package interview.guide.modules.interview.agent.adaptive.assessment;

/**
 * 评估报告事实来源接口。
 */
public interface AssessmentReportFactsSource {

  AssessmentReportFacts loadCandidate(String sessionId);

  AssessmentReportFacts loadEnterprise(String tenantId, String sessionId);
}
