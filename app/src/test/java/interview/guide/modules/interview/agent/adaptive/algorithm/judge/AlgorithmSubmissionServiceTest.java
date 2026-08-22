package interview.guide.modules.interview.agent.adaptive.algorithm.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    when(persistenceService.createPending(new CreateSandboxExecution(
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
    when(persistenceService.createPending(org.mockito.ArgumentMatchers.any()))
        .thenReturn(execution());
    when(producer.sendExecution("execution-1")).thenReturn(false);

    assertThatThrownBy(() -> service.submit(submission()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("入队失败");
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
        SandboxExecutionStatus.PENDING,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        LocalDateTime.now(),
        null,
        null
    );
  }
}
