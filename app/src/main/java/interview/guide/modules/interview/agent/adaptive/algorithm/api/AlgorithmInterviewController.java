package interview.guide.modules.interview.agent.adaptive.algorithm.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmProblemService;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.PublicAlgorithmProblem;
import interview.guide.modules.interview.agent.adaptive.algorithm.SubmitAlgorithmCode;
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
public class AlgorithmInterviewController {

  private final AlgorithmSubmissionService submissionService;
  private final AlgorithmProblemService problemService;

  @GetMapping("/algorithm/problems/{problemId}")
  public Result<PublicAlgorithmProblem> getProblem(@PathVariable String problemId) {
    return Result.success(problemService.getPublicProblem(problemId));
  }

  @PostMapping("/{sessionId}/algorithm/submissions")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 20)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<SandboxExecutionResponse> submit(
      @PathVariable String sessionId,
      @Valid @RequestBody SubmitAlgorithmCodeRequest request
  ) {
    return Result.success(SandboxExecutionResponse.from(submissionService.submit(
        new SubmitAlgorithmCode(
            sessionId,
            request.turnIndex(),
            request.problemId(),
            request.language(),
            request.source(),
            request.runMode()
        )
    )));
  }

  @GetMapping("/{sessionId}/algorithm/submissions/{submissionId}")
  public Result<SandboxExecutionResponse> getSubmission(
      @PathVariable String sessionId,
      @PathVariable String submissionId
  ) {
    return Result.success(SandboxExecutionResponse.from(
        submissionService.get(sessionId, submissionId)
    ));
  }
}
