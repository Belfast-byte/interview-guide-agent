package interview.guide.modules.interview.agent.adaptive.algorithm.api;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.AuthenticatedUser;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblemService;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmSubmissionService;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.PublicAlgorithmProblem;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.SubmitAlgorithmCode;
import interview.guide.modules.interview.agent.adaptive.persistence.session.AdaptiveInterviewPersistenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 算法面试 REST 控制器，提供提交代码、查询沙箱执行等接口。
 */
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
  private final AdaptiveInterviewPersistenceService persistenceService;

  @GetMapping("/algorithm/problems/{problemId}")
  public Result<PublicAlgorithmProblem> getProblem(@PathVariable String problemId) {
    return Result.success(problemService.getPublicProblem(problemId));
  }

  @GetMapping("/{sessionId}/algorithm/problems/{problemId}/variant")
  public Result<PublicAlgorithmProblem> selectProblemVariant(
      @PathVariable String sessionId,
      @PathVariable String problemId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    requireOwnership(principal, sessionId);
    return Result.success(problemService.selectPublicVariant(sessionId, problemId));
  }

  @PostMapping("/{sessionId}/algorithm/submissions")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 20)
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
  public Result<SandboxExecutionResponse> submit(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody SubmitAlgorithmCodeRequest request
  ) {
    requireOwnership(principal, sessionId);
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
      @PathVariable String submissionId,
      @AuthenticationPrincipal AuthenticatedUser principal
  ) {
    requireOwnership(principal, sessionId);
    return Result.success(SandboxExecutionResponse.from(
        submissionService.get(sessionId, submissionId)
    ));
  }

  @GetMapping("/{sessionId}/algorithm/submissions/latest")
  public Result<SandboxExecutionResponse> getLatestSubmission(
      @PathVariable String sessionId,
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam int turnIndex
  ) {
    requireOwnership(principal, sessionId);
    return Result.success(SandboxExecutionResponse.from(
        submissionService.getLatest(sessionId, turnIndex)
    ));
  }

  private void requireOwnership(AuthenticatedUser principal, String sessionId) {
    persistenceService.requireCandidateSession(
        principal.candidateId().toString(),
        sessionId
    );
  }
}
