package interview.guide.modules.interview.agent.adaptive.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmJudgeStreamProducer;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmPersistenceService;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSourceStorage;
import interview.guide.modules.interview.agent.adaptive.algorithm.CreateSandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxLanguage;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxWorkloadType;
import interview.guide.modules.interview.agent.adaptive.algorithm.StoredAlgorithmSource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodePatchSubmissionServiceTest {

  @Mock
  private CodeAnalysisPersistenceService codeAnalysisPersistenceService;

  @Mock
  private AlgorithmSourceStorage sourceStorage;

  @Mock
  private AlgorithmPersistenceService sandboxPersistenceService;

  @Mock
  private AlgorithmJudgeStreamProducer producer;

  private CodePatchSubmissionService service;

  @BeforeEach
  void setUp() {
    service = new CodePatchSubmissionService(
        codeAnalysisPersistenceService,
        sourceStorage,
        sandboxPersistenceService,
        producer
    );
  }

  @Test
  @DisplayName("PATCH 使用仓库快照和预置测试进入既有判题 Stream")
  void shouldSubmitPatchThroughExistingSandboxQueue() {
    when(codeAnalysisPersistenceService.getPatchTarget("session-1", "scenario-1"))
        .thenReturn(new PatchScenarioTarget(
            "scenario-1",
            "repos/one.zip",
            "tests/scenario-1.zip"
        ));
    when(sourceStorage.store(
        "session-1",
        SandboxLanguage.JAVA,
        "candidate patch"
    )).thenReturn(new StoredAlgorithmSource("patches/one.patch", "a".repeat(64)));
    when(sandboxPersistenceService.createPending(any())).thenReturn(pendingPatch());
    when(producer.sendExecution("execution-1")).thenReturn(true);

    SandboxExecution execution = service.submit(
        "session-1",
        3,
        "scenario-1",
        SandboxLanguage.JAVA,
        "candidate patch"
    );

    ArgumentCaptor<CreateSandboxExecution> command = ArgumentCaptor.forClass(
        CreateSandboxExecution.class
    );
    verify(sandboxPersistenceService).createPending(command.capture());
    assertThat(command.getValue().workloadType()).isEqualTo(SandboxWorkloadType.PATCH);
    assertThat(command.getValue().problemId()).isNull();
    assertThat(command.getValue().scenarioId()).isEqualTo("scenario-1");
    assertThat(command.getValue().workspaceRef()).isEqualTo("repos/one.zip");
    assertThat(command.getValue().testsRef()).isEqualTo("tests/scenario-1.zip");
    assertThat(execution.id()).isEqualTo("execution-1");
    verify(producer).sendExecution("execution-1");
  }

  private SandboxExecution pendingPatch() {
    return new SandboxExecution(
        "execution-1",
        "session-1",
        10L,
        1,
        SandboxWorkloadType.PATCH,
        null,
        "scenario-1",
        "repos/one.zip",
        "tests/scenario-1.zip",
        SandboxLanguage.JAVA,
        "patches/one.patch",
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
        false,
        0,
        LocalDateTime.now(),
        null,
        null
    );
  }
}
