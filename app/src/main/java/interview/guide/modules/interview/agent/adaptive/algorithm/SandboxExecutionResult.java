package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.util.List;

/**
 * 沙箱执行结果。
 */
public record SandboxExecutionResult(
    SandboxVerdict verdict,
    int passed,
    int total,
    long timeMs,
    long memoryKb,
    Integer firstFailedCase,
    List<SandboxExecutionLog> logs,
    SandboxPolicyViolation policyViolation
) {

  public SandboxExecutionResult(
      SandboxVerdict verdict,
      int passed,
      int total,
      long timeMs,
      long memoryKb,
      Integer firstFailedCase,
      List<SandboxExecutionLog> logs
  ) {
    this(verdict, passed, total, timeMs, memoryKb, firstFailedCase, logs, null);
  }
}
