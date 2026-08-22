package interview.guide.modules.interview.agent.adaptive.codeanalysis.job;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.CodeAnalysisResultAcceptanceService;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.repo.ProjectRepositorySnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码分析 Worker 回调控制器。
 */
@RestController
@RequestMapping("/internal/code-analysis/jobs")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.interview.code-analysis",
    name = "worker-enabled",
    havingValue = "true"
)
public class CodeAnalysisWorkerController {

  private static final String TOKEN_HEADER = "X-Code-Analysis-Token";

  private final CodeAnalysisPersistenceService persistenceService;
  private final CodeAnalysisResultAcceptanceService resultAcceptanceService;
  private final CodeAnalysisProperties properties;

  @GetMapping("/{jobId}")
  public Result<CodeAnalysisWorkerJobResponse> getJob(
      @PathVariable String jobId,
      @RequestHeader(TOKEN_HEADER) String token
  ) {
    authenticate(token);
    ProjectRepositorySnapshot snapshot = persistenceService.getRepositorySnapshot(jobId);
    return Result.success(new CodeAnalysisWorkerJobResponse(
        jobId,
        snapshot.repositoryRef(),
        snapshot.commitHash()
    ));
  }

  @PostMapping("/{jobId}/started")
  public Result<Void> started(
      @PathVariable String jobId,
      @RequestHeader(TOKEN_HEADER) String token
  ) {
    authenticate(token);
    persistenceService.markRunning(jobId);
    return Result.success();
  }

  @PostMapping("/{jobId}/result")
  public Result<Void> acceptResult(
      @PathVariable String jobId,
      @RequestHeader(TOKEN_HEADER) String token,
      @Valid @RequestBody CodeAnalysisResultRequest request
  ) {
    authenticate(token);
    resultAcceptanceService.accept(jobId, request.toDomain());
    return Result.success();
  }

  @PostMapping("/{jobId}/failed")
  public Result<Void> failed(
      @PathVariable String jobId,
      @RequestHeader(TOKEN_HEADER) String token,
      @Valid @RequestBody CodeAnalysisFailureRequest request
  ) {
    authenticate(token);
    persistenceService.markFailed(jobId, request.reason());
    return Result.success();
  }

  private void authenticate(String token) {
    if (!properties.getWorkerToken().equals(token)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "代码分析 Worker 凭证无效");
    }
  }
}
