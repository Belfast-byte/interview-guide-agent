package interview.guide.modules.interview.agent.adaptive.algorithm.api;

import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxRunMode;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxVerdict;

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
    boolean pendingRejudge
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
        execution.pendingRejudge()
    );
  }
}
