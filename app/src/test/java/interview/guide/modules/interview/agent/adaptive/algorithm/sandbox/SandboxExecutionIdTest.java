package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SandboxExecutionIdTest {

  @Test
  @DisplayName("相同业务负载忽略对象存储引用差异并生成同一执行 ID")
  void shouldCreateStableIdForSameWorkload() {
    CreateSandboxExecution first = command("source/first.java", "a".repeat(64));
    CreateSandboxExecution retry = command("source/retry.java", "a".repeat(64));

    assertThat(SandboxExecutionId.from(first)).isEqualTo(SandboxExecutionId.from(retry));
  }

  @Test
  @DisplayName("源码变化时生成新的执行 ID")
  void shouldCreateNewIdForChangedSource() {
    CreateSandboxExecution first = command("source/first.java", "a".repeat(64));
    CreateSandboxExecution changed = command("source/second.java", "b".repeat(64));

    assertThat(SandboxExecutionId.from(first)).isNotEqualTo(SandboxExecutionId.from(changed));
  }

  private CreateSandboxExecution command(String codeRef, String codeHash) {
    return new CreateSandboxExecution(
        "session-1",
        1,
        "two-sum",
        SandboxLanguage.JAVA,
        codeRef,
        codeHash,
        SandboxRunMode.FULL
    );
  }
}
