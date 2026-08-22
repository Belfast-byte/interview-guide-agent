package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

/**
 * 判题结果摘要格式，统一沙箱执行结果的文本摘要。
 */
public final class SandboxExecutionSummary {

  private SandboxExecutionSummary() {}

  public static String of(
      SandboxVerdict verdict,
      Integer passed,
      Integer total,
      Long timeMs,
      Long memoryKb,
      Integer firstFailedCase
  ) {
    return "verdict=%s, passed=%s/%s, timeMs=%s, memoryKb=%s, firstFailedCase=%s"
        .formatted(verdict, passed, total, timeMs, memoryKb, firstFailedCase);
  }
}
