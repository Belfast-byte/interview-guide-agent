package interview.guide.modules.interview.agent.adaptive.algorithm;

public record SandboxExecution(
    String id,
    String sessionId,
    long turnId,
    int submissionSeq,
    String problemId,
    SandboxLanguage language,
    String codeRef,
    String codeHash,
    SandboxRunMode runMode,
    SandboxExecutionStatus status,
    SandboxVerdict verdict,
    Integer passed,
    Integer total,
    Long timeMs,
    Long memoryKb,
    Integer firstFailedCase,
    String supersededBy,
    boolean pendingRejudge,
    int retryCount
) {}
