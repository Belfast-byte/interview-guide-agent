package interview.guide.modules.interview.agent.adaptive.codeanalysis.trace;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeTracePersistenceServiceTest {

  @Mock
  private CodeTraceCallRepository traceCallRepository;

  @InjectMocks
  private CodeTracePersistenceService service;

  @Test
  @DisplayName("第四次代码追踪在配额校验阶段被确定性拒绝")
  void shouldRejectFourthTraceCallOnQuotaCheck() {
    when(traceCallRepository.countBySessionId("session-1")).thenReturn(3L);

    assertThatThrownBy(() -> service.verifyQuota("session-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("次数已达上限");
  }

  @Test
  @DisplayName("未超限时配额校验通过且不落库")
  void shouldPassQuotaCheckWithoutPersisting() {
    when(traceCallRepository.countBySessionId("session-1")).thenReturn(2L);

    assertThatCode(() -> service.verifyQuota("session-1")).doesNotThrowAnyException();

    verify(traceCallRepository, never()).save(any());
  }

  @Test
  @DisplayName("追踪成功后按查询摘要记录一次额度消耗")
  void shouldRecordQuotaConsumptionAfterSuccessfulTrace() {
    service.record("session-1", "OrderService");

    verify(traceCallRepository).save(any(CodeTraceCallEntity.class));
  }
}
