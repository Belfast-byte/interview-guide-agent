package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveAgentSessionEntity;
import interview.guide.modules.interview.agent.adaptive.persistence.AdaptiveAgentSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodeTracePersistenceServiceTest {

  @Mock
  private AdaptiveAgentSessionRepository sessionRepository;

  @Mock
  private CodeTraceCallRepository traceCallRepository;

  @Mock
  private AdaptiveAgentSessionEntity session;

  @Test
  @DisplayName("第四次代码追踪在持久化前被确定性拒绝")
  void shouldRejectFourthTraceCall() {
    when(sessionRepository.findLockedById("session-1")).thenReturn(Optional.of(session));
    when(traceCallRepository.countBySessionId("session-1")).thenReturn(3L);
    CodeTracePersistenceService service = new CodeTracePersistenceService(
        sessionRepository,
        traceCallRepository
    );

    assertThatThrownBy(() -> service.reserve("session-1", "OrderService"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("次数已达上限");

    verify(traceCallRepository, never()).save(any());
  }
}
