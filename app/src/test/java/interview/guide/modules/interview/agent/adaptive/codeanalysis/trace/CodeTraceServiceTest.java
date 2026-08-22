package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.codeanalysis.job.CodeAnalysisPersistenceService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeTraceServiceTest {

  @Mock
  private CodeAnalysisPersistenceService analysisPersistenceService;

  @Mock
  private CodeTracePersistenceService tracePersistenceService;

  @Mock
  private CodeTraceSource traceSource;

  @InjectMocks
  private CodeTraceService service;

  @Test
  @DisplayName("追踪成功后才记录额度消耗")
  void shouldRecordQuotaOnlyAfterSuccessfulTrace() {
    when(analysisPersistenceService.getTraceRepositoryRef("session-1"))
        .thenReturn("s3://repos/one.zip");
    when(traceSource.trace(null, "session-1", "s3://repos/one.zip", "缓存失效", 10))
        .thenReturn(List.of());

    CodeTraceResult result = service.trace(null, "session-1", "缓存失效");

    assertThat(result.query()).isEqualTo("缓存失效");
    verify(tracePersistenceService).record("session-1", "缓存失效");
  }

  @Test
  @DisplayName("追踪失败不消耗额度")
  void shouldNotConsumeQuotaWhenTraceFails() {
    when(analysisPersistenceService.getTraceRepositoryRef("session-1"))
        .thenReturn("s3://repos/one.zip");
    doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "代码追踪执行失败"))
        .when(traceSource)
        .trace(
            org.mockito.ArgumentMatchers.nullable(String.class),
            anyString(),
            anyString(),
            anyString(),
            anyInt()
        );

    assertThatThrownBy(() -> service.trace(null, "session-1", "缓存失效"))
        .isInstanceOf(BusinessException.class);

    verify(tracePersistenceService, never()).record(anyString(), anyString());
  }
}
