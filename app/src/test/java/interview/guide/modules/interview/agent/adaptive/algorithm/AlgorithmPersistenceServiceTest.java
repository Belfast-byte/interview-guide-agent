package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmPersistenceServiceTest {

  @Mock
  private AlgorithmProblemRepository problemRepository;

  @Mock
  private SandboxExecutionRepository executionRepository;

  @Mock
  private SandboxExecutionLogRepository logRepository;

  @Mock
  private AlgorithmSessionFacts sessionFacts;

  private AlgorithmPersistenceService service;

  @BeforeEach
  void setUp() {
    AlgorithmInterviewProperties properties = new AlgorithmInterviewProperties();
    properties.setMaxExecutionsPerSession(20);
    service = new AlgorithmPersistenceService(
        problemRepository,
        executionRepository,
        logRepository,
        sessionFacts,
        properties
    );
  }

  @Nested
  @DisplayName("创建判题提交")
  class CreateExecution {

    @Test
    @DisplayName("锁定当前轮次后创建递增序号的待判题记录")
    void shouldCreatePendingExecution() {
      CreateSandboxExecution command = command();
      SandboxExecutionEntity previous = new SandboxExecutionEntity(
          "previous",
          command,
          9L,
          2
      );
      when(sessionFacts.lockCurrentTurn("session-1", 1)).thenReturn(9L);
      when(problemRepository.existsById("two-sum")).thenReturn(true);
      when(executionRepository.countBySessionId("session-1")).thenReturn(2L);
      when(executionRepository.findTopBySessionIdOrderBySubmissionSeqDesc("session-1"))
          .thenReturn(Optional.of(previous));
      when(executionRepository.save(org.mockito.ArgumentMatchers.any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      SandboxExecution execution = service.createPending(command);

      assertThat(execution.submissionSeq()).isEqualTo(3);
      assertThat(execution.status()).isEqualTo(SandboxExecutionStatus.PENDING);
      assertThat(execution.turnId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("第 21 次执行在写入前被拒绝")
    void shouldRejectExecutionBeyondSessionQuota() {
      CreateSandboxExecution command = command();
      when(sessionFacts.lockCurrentTurn("session-1", 1)).thenReturn(9L);
      when(problemRepository.existsById("two-sum")).thenReturn(true);
      when(executionRepository.countBySessionId("session-1")).thenReturn(20L);

      assertThatThrownBy(() -> service.createPending(command))
          .isInstanceOfSatisfying(BusinessException.class, exception ->
              assertThat(exception.getCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()));
      verify(executionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
  }

  private CreateSandboxExecution command() {
    return new CreateSandboxExecution(
        "session-1",
        1,
        "two-sum",
        SandboxLanguage.JAVA,
        "sandbox/source/source.java",
        "a".repeat(64),
        SandboxRunMode.FULL
    );
  }
}
