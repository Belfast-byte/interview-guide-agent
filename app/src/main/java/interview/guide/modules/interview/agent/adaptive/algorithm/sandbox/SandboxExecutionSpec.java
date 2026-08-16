package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

/**
 * 沙箱执行规格。
 */
public record SandboxExecutionSpec(
    String referenceId,
    String casesRef,
    String workspaceRef,
    int timeLimitMs,
    int memoryLimitKb
) {}
