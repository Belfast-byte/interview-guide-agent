package interview.guide.modules.interview.agent.adaptive.algorithm;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
      AlgorithmProblem problem
  ) {
    String casesRef = execution.runMode() == SandboxRunMode.SAMPLE
        ? problem.sampleCasesRef()
        : problem.hiddenCasesRef();
    SandboxdResponse response = restClient.post()
        .uri("/internal/executions")
        .body(new SandboxdRequest(
            execution.id(),
            execution.problemId(),
            execution.language(),
            execution.codeRef(),
            casesRef,
            execution.runMode(),
            problem.timeLimitMs(),
            problem.memoryLimitKb()
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
        response.logs()
    );
  }

  private record SandboxdRequest(
      String executionId,
      String problemId,
      SandboxLanguage language,
      String codeRef,
      String casesRef,
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
      List<SandboxExecutionLog> logs
  ) {}
}
