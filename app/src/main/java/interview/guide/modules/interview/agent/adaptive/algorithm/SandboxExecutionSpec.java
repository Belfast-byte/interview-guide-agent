package interview.guide.modules.interview.agent.adaptive.algorithm;

public record SandboxExecutionSpec(
    String referenceId,
    String casesRef,
    String workspaceRef,
    int timeLimitMs,
    int memoryLimitKb
) {}
