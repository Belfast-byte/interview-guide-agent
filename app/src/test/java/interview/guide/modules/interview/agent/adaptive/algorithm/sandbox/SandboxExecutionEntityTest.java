package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SandboxExecutionEntityTest {

  @Test
  @DisplayName("候选人代码判题结果完整写入执行事实")
  void shouldPersistCandidateVerdictFacts() {
    SandboxExecutionEntity execution = execution();
    SandboxExecutionResult result = new SandboxExecutionResult(
        SandboxVerdict.WA,
        4,
        10,
        120,
        32_768,
        7,
        List.of(new SandboxExecutionLog(
            SandboxLogType.TEST_CASES,
            "sandbox/logs/execution-1/cases.json"
        )),
        SandboxPolicyViolation.NETWORK_ACCESS
    );

    execution.markRunning();
    execution.apply(result);

    assertThat(execution.toDomain()).satisfies(saved -> {
      assertThat(saved.status()).isEqualTo(SandboxExecutionStatus.DONE);
      assertThat(saved.verdict()).isEqualTo(SandboxVerdict.WA);
      assertThat(saved.passed()).isEqualTo(4);
      assertThat(saved.total()).isEqualTo(10);
      assertThat(saved.firstFailedCase()).isEqualTo(7);
      assertThat(saved.policyViolation()).isEqualTo(SandboxPolicyViolation.NETWORK_ACCESS);
    });
  }

  @Test
  @DisplayName("沙箱内部错误直接落为终态 IE，不再实体级自动重判")
  void shouldFinalizeInternalErrorWithoutEntityRejudge() {
    SandboxExecutionEntity execution = execution();
    SandboxExecutionResult internalError = new SandboxExecutionResult(
        SandboxVerdict.IE,
        0,
        0,
        0,
        0,
        null,
        List.of(),
        null
    );

    execution.markRunning();
    execution.apply(internalError);

    assertThat(execution.isTerminal()).isTrue();
    assertThat(execution.toDomain().status()).isEqualTo(SandboxExecutionStatus.DONE);
    assertThat(execution.toDomain().verdict()).isEqualTo(SandboxVerdict.IE);
  }

  @Test
  @DisplayName("终态执行可被识别，供迟到结果守卫使用")
  void shouldReportTerminalStatus() {
    SandboxExecutionEntity execution = execution();

    assertThat(execution.isTerminal()).isFalse();
    execution.markQueuedTimeout();
    assertThat(execution.isTerminal()).isTrue();
  }

  @Test
  @DisplayName("新代码提交后旧执行保留事实但标记为过期")
  void shouldMarkExecutionSuperseded() {
    SandboxExecutionEntity execution = execution();

    execution.supersedeWith("execution-2");

    assertThat(execution.toDomain().supersededBy()).isEqualTo("execution-2");
  }

  @Test
  @DisplayName("只有排队中的执行可以进入排队超时降级")
  void shouldTimeoutOnlyQueuedExecution() {
    SandboxExecutionEntity execution = execution();

    assertThat(execution.markQueuedTimeout()).isTrue();
    assertThat(execution.toDomain().status())
        .isEqualTo(SandboxExecutionStatus.TIMEOUT_QUEUED);
    assertThat(execution.markRunning()).isFalse();
  }

  @Test
  @DisplayName("RUNNING 超龄回收为排队超时并记录 IE 待重判")
  void shouldRecycleStuckRunningAsQueuedTimeout() {
    SandboxExecutionEntity execution = execution();
    assertThat(execution.markRunning()).isTrue();

    assertThat(execution.markStuckRunningTimeout()).isTrue();
    assertThat(execution.toDomain()).satisfies(recycled -> {
      assertThat(recycled.status()).isEqualTo(SandboxExecutionStatus.TIMEOUT_QUEUED);
      assertThat(recycled.verdict()).isEqualTo(SandboxVerdict.IE);
      assertThat(recycled.finishedAt()).isNotNull();
    });
  }

  @Test
  @DisplayName("非 RUNNING 执行不可被卡死回收")
  void shouldNotRecycleNonRunningExecution() {
    SandboxExecutionEntity pending = execution();
    assertThat(pending.markStuckRunningTimeout()).isFalse();

    SandboxExecutionEntity done = execution();
    done.markRunning();
    done.apply(new SandboxExecutionResult(
        SandboxVerdict.AC,
        3,
        3,
        100,
        1024,
        null,
        List.of(),
        null
    ));
    assertThat(done.markStuckRunningTimeout()).isFalse();
  }

  @Test
  @DisplayName("结果已落库后通知失败不得把真实判题覆盖为基础设施错误")
  void shouldPreserveCompletedVerdictWhenNotificationFails() {
    SandboxExecutionEntity execution = execution();
    execution.markRunning();
    execution.apply(new SandboxExecutionResult(
        SandboxVerdict.AC,
        3,
        3,
        100,
        1024,
        null,
        List.of(),
        null
    ));

    execution.markInfrastructureFailure();

    assertThat(execution.toDomain()).satisfies(completed -> {
      assertThat(completed.status()).isEqualTo(SandboxExecutionStatus.DONE);
      assertThat(completed.verdict()).isEqualTo(SandboxVerdict.AC);
      assertThat(completed.passed()).isEqualTo(3);
    });
  }

  private SandboxExecutionEntity execution() {
    return new SandboxExecutionEntity(
        "execution-1",
        new CreateSandboxExecution(
            "session-1",
            1,
            "two-sum",
            SandboxLanguage.JAVA,
            "sandbox/source/execution-1.java",
            "a".repeat(64),
            SandboxRunMode.FULL
        ),
        10L,
        1
    );
  }
}
