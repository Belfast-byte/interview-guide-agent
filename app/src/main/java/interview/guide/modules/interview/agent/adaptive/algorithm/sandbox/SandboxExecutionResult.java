package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

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
) {}
