package interview.guide.modules.interview.agent.adaptive.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SandboxExecutionEntityTest {

  @Test
  @DisplayName("沙箱内部错误自动重试一次，第二次内部错误进入待重判")
  void shouldRetryInternalErrorOnce() {
    SandboxExecutionEntity execution = execution();
    SandboxExecutionResult internalError = new SandboxExecutionResult(
        SandboxVerdict.IE,
        0,
        0,
        0,
        0,
        null,
        List.of()
    );

    assertThat(execution.markRunning()).isTrue();
    assertThat(execution.apply(internalError)).isTrue();
    assertThat(execution.toDomain().status()).isEqualTo(SandboxExecutionStatus.PENDING);
    assertThat(execution.toDomain().retryCount()).isEqualTo(1);
    assertThat(execution.toDomain().verdict()).isNull();

    assertThat(execution.markRunning()).isTrue();
    assertThat(execution.apply(internalError)).isFalse();
    assertThat(execution.toDomain().status()).isEqualTo(SandboxExecutionStatus.DONE);
    assertThat(execution.toDomain().verdict()).isEqualTo(SandboxVerdict.IE);
    assertThat(execution.toDomain().pendingRejudge()).isTrue();
  }

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
        ))
    );

    execution.markRunning();

    assertThat(execution.apply(result)).isFalse();
    assertThat(execution.toDomain()).satisfies(saved -> {
      assertThat(saved.status()).isEqualTo(SandboxExecutionStatus.DONE);
      assertThat(saved.verdict()).isEqualTo(SandboxVerdict.WA);
      assertThat(saved.passed()).isEqualTo(4);
      assertThat(saved.total()).isEqualTo(10);
      assertThat(saved.firstFailedCase()).isEqualTo(7);
      assertThat(saved.pendingRejudge()).isFalse();
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
