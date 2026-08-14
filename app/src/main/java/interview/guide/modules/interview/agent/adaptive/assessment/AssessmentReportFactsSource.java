package interview.guide.modules.interview.agent.adaptive.assessment;

public interface AssessmentReportFactsSource {

  AssessmentReportFacts loadCandidate(String sessionId);

  AssessmentReportFacts loadEnterprise(String tenantId, String sessionId);
}
