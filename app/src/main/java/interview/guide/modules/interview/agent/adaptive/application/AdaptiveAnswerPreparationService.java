package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.planning.PlannedInterview;
import org.springframework.stereotype.Service;

/** 执行回答推进中两个互不持久化中间状态的外部步骤。 */
@Service
public class AdaptiveAnswerPreparationService {

  private final AdaptiveAnswerAssessmentService assessmentService;
  private final SandboxSubmissionApplicationService sandboxSubmissions;

  public AdaptiveAnswerPreparationService(
      AdaptiveAnswerAssessmentService assessmentService,
      SandboxSubmissionApplicationService sandboxSubmissions
  ) {
    this.assessmentService = assessmentService;
    this.sandboxSubmissions = sandboxSubmissions;
  }

  public AnswerAssessment prepare(PlannedInterview interview, CandidateAnswer answer) {
    sandboxSubmissions.submit(interview.history().session().id(), answer);
    return assessmentService.assess(interview, answer);
  }
}
