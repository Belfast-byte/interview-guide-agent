package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

import java.time.LocalDateTime;

/**
 * 沙箱执行值对象。
 */
public record SandboxExecution(
    String id,
    String sessionId,
    long turnId,
    int submissionSeq,
    SandboxWorkloadType workloadType,
    String problemId,
    String scenarioId,
    String workspaceRef,
    String testsRef,
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
    LocalDateTime createdAt,
    LocalDateTime finishedAt,
    SandboxPolicyViolation policyViolation
) {}
