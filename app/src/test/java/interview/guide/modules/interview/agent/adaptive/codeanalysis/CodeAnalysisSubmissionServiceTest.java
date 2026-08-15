package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import java.time.LocalDateTime;
import interview.guide.modules.interview.agent.adaptive.observability.CodeAnalysisTelemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisSubmissionServiceTest {

  @Mock
  private CodeAnalysisPersistenceService persistenceService;

  @Mock
  private CodeAnalysisStreamProducer producer;

  @Mock
  private CodeAnalysisTelemetry telemetry;

  @InjectMocks
  private CodeAnalysisSubmissionService service;

  @Test
  @DisplayName("任务落库后投递 Stream，投递失败快速失败")
  void shouldFailFastWhenEnqueueFails() {
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
    when(persistenceService.createJob(
        "session-1",
        "tenant-1",
        "s3://repos/one.zip",
        "abc123",
        expiresAt
    )).thenReturn(new CodeAnalysisJob(
        "job-1",
        "session-1",
        "repo-1",
        AnalysisJobStatus.PENDING,
        null,
        null,
        LocalDateTime.now(),
        null
    ));
    when(producer.send("job-1")).thenReturn(false);

    assertThatThrownBy(() -> service.submit(
        "session-1",
        "tenant-1",
        "s3://repos/one.zip",
        "abc123",
        expiresAt
    )).isInstanceOf(BusinessException.class)
        .hasMessageContaining("投递失败");
    verify(producer).send("job-1");
  }
}
