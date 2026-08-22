package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.AssessmentReportService;
import interview.guide.modules.interview.agent.adaptive.assessment.report.CandidateAssessmentReport;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateAnswer;
import interview.guide.modules.interview.agent.adaptive.core.event.CandidateCodeSubmission;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultFollowUp;
import interview.guide.modules.interview.agent.adaptive.memory.profile.CandidateAbilityProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private final CandidateAbilityProfileService abilityProfileService;

  @PostMapping
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<AdaptiveInterviewResponse> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CreateAdaptiveInterviewRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.create(
            candidateId(principal),
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
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody SubmitAdaptiveAnswerRequest request
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.submitAnswerForCandidate(
            candidateId(principal),
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
  public Result<AdaptiveInterviewResponse> get(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(AdaptiveInterviewResponse.from(
        applicationService.getForCandidate(candidateId(principal), sessionId)
    ));
  }

  @GetMapping("/me/ability-profile")
  public Result<CandidateAbilityProfileResponse> getAbilityProfile(
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    String candidateId = candidateId(principal);
    return Result.success(CandidateAbilityProfileResponse.from(
        candidateId,
        abilityProfileService.trajectory(candidateId)
    ));
  }

  @GetMapping("/{sessionId}/report")
  public Result<CandidateAssessmentReport> getReport(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    applicationService.requireCandidateSession(candidateId(principal), sessionId);
    return Result.success(reportService.candidateReport(sessionId));
  }

  @GetMapping("/{sessionId}/tool-result-follow-ups")
  public Result<List<ToolResultFollowUp>> getToolResultFollowUps(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    return Result.success(applicationService.toolResultFollowUpsForCandidate(
        candidateId(principal),
        sessionId
    ));
  }

  private String candidateId(AuthenticatedUser principal) {
    return principal.candidateId().toString();
  }
}
