package interview.guide.modules.interview.agent.adaptive.algorithm.sandbox;

/**
 * 沙箱执行日志。
 */
public record SandboxExecutionLog(
    SandboxLogType type,
    String storageRef
) {}
