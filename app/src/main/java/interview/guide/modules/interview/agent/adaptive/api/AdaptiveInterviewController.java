package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.assessment.AssessmentBackfillService;
import interview.guide.modules.interview.agent.adaptive.assessment.CandidateAssessmentReport;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.CandidateAbilityProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 自适应面试 REST 控制器，暴露创建、答题、查询、报告等接口。
 */
@RestController
@RequestMapping("/api/adaptive-agent-interviews")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
public class AdaptiveInterviewController {

  private final AdaptiveInterviewApplicationService applicationService;
  private final AssessmentReportService reportService;
  private final AssessmentBackfillService assessmentBackfillService;
  private final CandidateAbilityProfileService abilityProfileService;

  @PostMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<AdaptiveInterviewResponse> create(
      @Valid @RequestBody CreateAdaptiveInterviewRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.create(
            request.candidateId(),
            request.jd(),
            request.resume(),
            request.llmProvider()
        )
    ));
  }

  @PostMapping("/{sessionId}/answers")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
  public Result<AdaptiveInterviewResponse> submitAnswer(
      @PathVariable String sessionId,
      @Valid @RequestBody SubmitAdaptiveAnswerRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.submitAnswer(
            sessionId,
            new CandidateAnswer(
                request.turnIndex(),
                request.answer(),
                request.codeSubmission() == null
                    ? null
                    : new CandidateCodeSubmission(
                        request.codeSubmission().problemId(),
                        request.codeSubmission().scenarioId(),
                        request.codeSubmission().language().name(),
                        request.codeSubmission().runMode().name()
                    )
            )
        )
    ));
  }

  @GetMapping("/{sessionId}")
  public Result<AdaptiveInterviewResponse> get(@PathVariable String sessionId) {
    return Result.success(AdaptiveInterviewResponse.from(applicationService.get(sessionId)));
  }

  @PostMapping("/{sessionId}/assessment-backfill")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 2)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 2)
  public Result<AssessmentBackfillResponse> backfillAssessment(
      @PathVariable String sessionId
  ) {
    return Result.success(new AssessmentBackfillResponse(
        assessmentBackfillService.backfill(sessionId)
    ));
  }

  @PostMapping("/{sessionId}/code-analysis/replan")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 2)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 2)
  public Result<AdaptiveInterviewResponse> replanWithCodeAnalysis(
      @PathVariable String sessionId
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.replanWithCodeAnalysis(sessionId)
    ));
  }

  @GetMapping("/candidates/{candidateId}/ability-profile")
  public Result<CandidateAbilityProfileResponse> getAbilityProfile(
      @PathVariable String candidateId
  ) {
    return Result.success(CandidateAbilityProfileResponse.from(
        candidateId,
        abilityProfileService.trajectory(candidateId)
    ));
  }

  @GetMapping("/{sessionId}/report")
  public Result<CandidateAssessmentReport> getReport(
      @PathVariable String sessionId
  ) {
    return Result.success(reportService.candidateReport(sessionId));
  }

  @GetMapping("/{sessionId}/tool-result-follow-ups")
  public Result<List<ToolResultFollowUp>> getToolResultFollowUps(
      @PathVariable String sessionId
  ) {
    return Result.success(applicationService.toolResultFollowUps(sessionId));
  }
}
