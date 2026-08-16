package interview.guide.modules.interview.agent.adaptive.algorithm.api;

import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxVerdict;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxPolicyViolation;

/**
 * 沙箱执行结果响应，封装提交状态、评测结论与资源消耗。
 */
public record SandboxExecutionResponse(
    String submissionId,
    int submissionSeq,
    SandboxRunMode runMode,
    SandboxExecutionStatus status,
    SandboxVerdict verdict,
    Integer passed,
    Integer total,
    Long timeMs,
    Long memoryKb,
    Integer firstFailedCase,
    boolean pendingRejudge,
    SandboxPolicyViolation policyViolation
) {

  static SandboxExecutionResponse from(SandboxExecution execution) {
    return new SandboxExecutionResponse(
        execution.id(),
        execution.submissionSeq(),
        execution.runMode(),
        execution.status(),
        execution.verdict(),
        execution.passed(),
        execution.total(),
        execution.timeMs(),
        execution.memoryKb(),
        execution.firstFailedCase(),
        execution.pendingRejudge(),
        execution.policyViolation()
    );
  }
}
