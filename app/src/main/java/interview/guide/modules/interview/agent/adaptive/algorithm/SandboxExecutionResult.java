package interview.guide.modules.interview.agent.adaptive.algorithm;

import java.util.List;

public record SandboxExecutionResult(
    SandboxVerdict verdict,
    int passed,
    int total,
    long timeMs,
    long memoryKb,
    Integer firstFailedCase,
    List<SandboxExecutionLog> logs
) {}
