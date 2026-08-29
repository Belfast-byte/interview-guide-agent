package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.AlgorithmSourceStorage;
import interview.guide.modules.interview.agent.adaptive.algorithm.problem.StoredAlgorithmSource;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxWorkloadType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmSubmissionServiceTest {

  @Mock
  private AlgorithmSourceStorage sourceStorage;

  @Mock
  private AlgorithmPersistenceService persistenceService;

  @Mock
  private AlgorithmJudgeStreamProducer producer;

  private AlgorithmSubmissionService service;

  @BeforeEach
  void setUp() {
    service = new AlgorithmSubmissionService(sourceStorage, persistenceService, producer);
  }

  @Test
  @DisplayName("源码先写对象存储，执行事实落库后再投递判题任务")
  void shouldStoreCreateAndEnqueueSubmission() {
    SubmitAlgorithmCode submission = submission();
    StoredAlgorithmSource source = new StoredAlgorithmSource(
        "sandbox/sources/session-1/source.java",
        "a".repeat(64)
    );
    SandboxExecution execution = execution();
    when(sourceStorage.store("session-1", SandboxLanguage.JAVA, "class Main {}"))
        .thenReturn(source);
    when(persistenceService.createOrReuse(new CreateSandboxExecution(
        "session-1",
        1,
        "two-sum",
        SandboxLanguage.JAVA,
        source.codeRef(),
        source.codeHash(),
        SandboxRunMode.FULL
    ))).thenReturn(execution);
    when(producer.sendExecution("execution-1")).thenReturn(true);

    assertThat(service.submit(submission)).isEqualTo(execution);
    verify(producer).sendExecution("execution-1");
  }

  @Test
  @DisplayName("Redis 入队失败向调用方快速失败")
  void shouldFailWhenEnqueueFails() {
    when(sourceStorage.store("session-1", SandboxLanguage.JAVA, "class Main {}"))
        .thenReturn(new StoredAlgorithmSource("source-ref", "a".repeat(64)));
    when(persistenceService.createOrReuse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(execution());
    when(producer.sendExecution("execution-1")).thenReturn(false);

    assertThatThrownBy(() -> service.submit(submission()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("入队失败");
    verify(persistenceService, never()).markInfrastructureFailure("execution-1");
  }

  @Test
  @DisplayName("同一业务负载已有终态时直接复用且不重复入队")
  void shouldReuseTerminalExecutionWithoutEnqueue() {
    when(sourceStorage.store("session-1", SandboxLanguage.JAVA, "class Main {}"))
        .thenReturn(new StoredAlgorithmSource("source-ref", "a".repeat(64)));
    SandboxExecution completed = execution(SandboxExecutionStatus.DONE);
    when(persistenceService.createOrReuse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(completed);

    assertThat(service.submit(submission())).isSameAs(completed);

    verify(producer, never()).sendExecution("execution-1");
  }

  private SubmitAlgorithmCode submission() {
    return new SubmitAlgorithmCode(
        "session-1",
        1,
        "two-sum",
        SandboxLanguage.JAVA,
        "class Main {}",
        SandboxRunMode.FULL
    );
  }

  private SandboxExecution execution() {
    return execution(SandboxExecutionStatus.PENDING);
  }

  private SandboxExecution execution(SandboxExecutionStatus status) {
    return new SandboxExecution(
        "execution-1",
        "session-1",
        10L,
        1,
        SandboxWorkloadType.ALGORITHM,
        "two-sum",
        null,
        null,
        null,
        SandboxLanguage.JAVA,
        "source-ref",
        "a".repeat(64),
        SandboxRunMode.FULL,
        status,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LocalDateTime.now(),
        null,
        null
    );
  }
}
