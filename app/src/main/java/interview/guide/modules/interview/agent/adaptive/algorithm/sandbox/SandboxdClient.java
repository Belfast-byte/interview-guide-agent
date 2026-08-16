package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sandboxd 客户端，封装与沙箱服务的通信。
 */
@Component
class SandboxdClient implements SandboxWorker {

  private final RestClient restClient;

  SandboxdClient(AlgorithmInterviewProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getSandboxConnectTimeout());
    requestFactory.setReadTimeout(properties.getSandboxReadTimeout());
    restClient = RestClient.builder()
        .baseUrl(properties.getSandboxBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  @Override
  public SandboxExecutionResult execute(
      SandboxExecution execution,
      SandboxExecutionSpec spec
  ) {
    SandboxdResponse response = restClient.post()
        .uri("/internal/executions")
        .body(new SandboxdRequest(
            execution.id(),
            execution.workloadType(),
            spec.referenceId(),
            execution.language(),
            execution.codeRef(),
            spec.casesRef(),
            spec.workspaceRef(),
            execution.runMode(),
            spec.timeLimitMs(),
            spec.memoryLimitKb()
        ))
        .retrieve()
        .body(SandboxdResponse.class);
    if (response == null || response.verdict() == null || response.logs() == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "沙箱返回了无效结果");
    }
    return new SandboxExecutionResult(
        response.verdict(),
        response.passed(),
        response.total(),
        response.timeMs(),
        response.memoryKb(),
        response.firstFailedCase(),
        response.logs(),
        response.policyViolation()
    );
  }

  private record SandboxdRequest(
      String executionId,
      SandboxWorkloadType workloadType,
      String referenceId,
      SandboxLanguage language,
      String codeRef,
      String casesRef,
      String workspaceRef,
      SandboxRunMode runMode,
      int timeLimitMs,
      int memoryLimitKb
  ) {}

  private record SandboxdResponse(
      SandboxVerdict verdict,
      int passed,
      int total,
      long timeMs,
      long memoryKb,
      Integer firstFailedCase,
      List<SandboxExecutionLog> logs,
      SandboxPolicyViolation policyViolation
  ) {}
}
