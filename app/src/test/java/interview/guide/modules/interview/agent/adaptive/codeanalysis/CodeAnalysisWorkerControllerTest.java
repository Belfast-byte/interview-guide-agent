package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisWorkerControllerTest {

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private CodeAnalysisResultAcceptanceService resultAcceptanceService;

  private CodeAnalysisWorkerController controller;

  @BeforeEach
  void setUp() {
    CodeAnalysisProperties properties = new CodeAnalysisProperties();
    properties.setWorkerToken("worker-secret");
    controller = new CodeAnalysisWorkerController(
        persistenceService,
        resultAcceptanceService,
        properties
    );
  }

  @Test
  @DisplayName("独立分析 Worker 可读取任务并回写三类结构化产物")
  void shouldExposeWorkerLifecycle() {
    when(persistenceService.getRepositorySnapshot("job-1"))
        .thenReturn(new ProjectRepositorySnapshot("s3://repos/project.zip", "abc123"));
    ProjectDigest digest = new ProjectDigest(
        "digest-1",
        "abc123",
        List.of("Java"),
        List.of(),
        List.of(),
        List.of()
    );
    CodeAnalysisResultRequest request = new CodeAnalysisResultRequest(
        digest,
        List.of(),
        List.of(),
        1200,
        300
    );

    assertThat(controller.getJob("job-1", "worker-secret").getData())
        .isEqualTo(new CodeAnalysisWorkerJobResponse(
            "job-1",
            "s3://repos/project.zip",
            "abc123"
        ));
    controller.started("job-1", "worker-secret");
    controller.acceptResult("job-1", "worker-secret", request);
    controller.failed(
        "job-2",
        "worker-secret",
        new CodeAnalysisFailureRequest("repository clone failed")
    );

    verify(persistenceService).markRunning("job-1");
    verify(resultAcceptanceService).accept("job-1", request.toDomain());
    verify(persistenceService).markFailed("job-2", "repository clone failed");
  }

  @Test
  @DisplayName("分析 Worker 凭证错误时拒绝读取任务")
  void shouldRejectInvalidWorkerCredential() {
    assertThatThrownBy(() -> controller.getJob("job-1", "wrong-token"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("代码分析 Worker 凭证无效");
  }
}
