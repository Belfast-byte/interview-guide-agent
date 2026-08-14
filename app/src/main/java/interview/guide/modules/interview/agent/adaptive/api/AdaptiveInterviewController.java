package interview.guide.modules.interview.agent.adaptive.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.adaptive.application.AdaptiveInterviewApplicationService;
import interview.guide.modules.interview.agent.adaptive.core.CandidateAnswer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            new CandidateAnswer(request.turnIndex(), request.answer())
        )
    ));
  }

  @GetMapping("/{sessionId}")
  public Result<AdaptiveInterviewResponse> get(@PathVariable String sessionId) {
    return Result.success(AdaptiveInterviewResponse.from(applicationService.get(sessionId)));
  }
}
