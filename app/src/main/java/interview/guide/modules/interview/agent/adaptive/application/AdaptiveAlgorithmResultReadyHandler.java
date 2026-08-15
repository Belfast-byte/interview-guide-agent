package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmResultReadyHandler;
import interview.guide.modules.interview.agent.adaptive.algorithm.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.core.ToolResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdaptiveAlgorithmResultReadyHandler implements AlgorithmResultReadyHandler {

  private final AdaptiveInterviewApplicationService applicationService;
  private final AlgorithmSessionFacts sessionFacts;

  @Override
  public void handle(SandboxExecution execution) {
    if (execution.supersededBy() != null) {
      return;
    }
    String summary = execution.status() == SandboxExecutionStatus.TIMEOUT_QUEUED
        ? "status=TIMEOUT_QUEUED, judging unavailable; continue with code walkthrough and do not treat this as negative evidence"
        : "verdict=%s, passed=%s/%s, timeMs=%s, memoryKb=%s, firstFailedCase=%s"
            .formatted(
                execution.verdict(),
                execution.passed(),
                execution.total(),
                execution.timeMs(),
                execution.memoryKb(),
                execution.firstFailedCase()
            );
    applicationService.handleToolResult(
        execution.sessionId(),
        new ToolResultEvent(
            sessionFacts.turnIndex(execution.turnId()),
            "sandbox_submit",
            execution.id(),
            summary,
            summary
        )
    );
  }
}
