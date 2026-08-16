package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmInterviewProperties;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmProblemRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionEntity;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionLogRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionRepository;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import java.time.LocalDateTime;
import java.util.List;
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

  @Mock
  private AlgorithmInterviewTelemetry telemetry;

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
        properties,
        telemetry
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

  @Test
  @DisplayName("按会话和轮次读取最新代码提交")
  void shouldReadLatestExecutionForTurn() {
    CreateSandboxExecution command = command();
    SandboxExecutionEntity entity = new SandboxExecutionEntity(
        "execution-latest",
        command,
        9L,
        3
    );
    when(sessionFacts.turnId("session-1", 1)).thenReturn(9L);
    when(executionRepository.findTopBySessionIdAndTurnIdOrderBySubmissionSeqDesc(
        "session-1",
        9L
    )).thenReturn(Optional.of(entity));

    assertThat(service.getLatestExecution("session-1", 1).id())
        .isEqualTo("execution-latest");
  }

  @Test
  @DisplayName("RUNNING 超龄执行被回收为排队超时并标记基础设施失败待重判")
  void shouldRecycleStuckRunningBeforeCutoff() {
    SandboxExecutionEntity running = new SandboxExecutionEntity(
        "execution-stuck",
        command(),
        9L,
        1
    );
    assertThat(running.markRunning()).isTrue();
    when(executionRepository.findByStatusAndStartedAtBefore(
        eq(SandboxExecutionStatus.RUNNING),
        any()
    )).thenReturn(List.of(running));

    List<SandboxExecution> recycled = service.timeoutRunningBefore(
        LocalDateTime.now().minusSeconds(120)
    );
    assertThat(recycled).hasSize(1);
    assertThat(recycled.get(0)).satisfies(execution -> {
      assertThat(execution.status()).isEqualTo(SandboxExecutionStatus.TIMEOUT_QUEUED);
      assertThat(execution.verdict()).isEqualTo(SandboxVerdict.IE);
      assertThat(execution.pendingRejudge()).isTrue();
    });
  }

  @Test
  @DisplayName("非 RUNNING 执行不会被卡死回收")
  void shouldSkipNonRunningExecutionOnStuckRecycle() {
    SandboxExecutionEntity pending = new SandboxExecutionEntity(
        "execution-pending",
        command(),
        9L,
        1
    );
    when(executionRepository.findByStatusAndStartedAtBefore(
        eq(SandboxExecutionStatus.RUNNING),
        any()
    )).thenReturn(List.of(pending));

    assertThat(service.timeoutRunningBefore(LocalDateTime.now().minusSeconds(120)))
        .isEmpty();
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
