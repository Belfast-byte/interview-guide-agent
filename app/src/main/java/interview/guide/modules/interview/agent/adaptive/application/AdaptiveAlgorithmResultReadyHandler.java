package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmResultReadyHandler;
import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmSessionFacts;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionStatus;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecutionSummary;
import interview.guide.modules.interview.agent.adaptive.core.event.ToolResultEvent;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 算法评测结果就绪事件处理器，负责将异步评测结果回写并触发重新评估。
 */
@Component
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
class AdaptiveAlgorithmResultReadyHandler implements AlgorithmResultReadyHandler {

  private final AdaptiveInterviewApplicationService applicationService;
  private final AlgorithmSessionFacts sessionFacts;
  private final AlgorithmInterviewTelemetry telemetry;

  @Override
  public void handle(SandboxExecution execution) {
    telemetry.resultReady(execution);
    if (execution.supersededBy() != null) {
      return;
    }
    String summary = execution.status() == SandboxExecutionStatus.TIMEOUT_QUEUED
        ? "status=TIMEOUT_QUEUED, judging unavailable"
        : SandboxExecutionSummary.of(
            execution.verdict(),
            execution.passed(),
            execution.total(),
            execution.timeMs(),
            execution.memoryKb(),
            execution.firstFailedCase()
        );
    int turnIndex = sessionFacts.turnIndex(execution.turnId());
    ToolResultEvent event = new ToolResultEvent(
        turnIndex,
        AlgorithmResultReadyHandler.SANDBOX_SUBMIT_TOOL_NAME,
        execution.id(),
        summary,
        summary
    );
    // 预留去重先于任何 LLM 调用：重复投递直接幂等返回，不重复烧重评
    if (!applicationService.reserveToolResultEvent(execution.sessionId(), event)) {
      telemetry.resultReadyDeduped();
      return;
    }
    try {
      applicationService.handleToolResult(execution.sessionId(), event);
    } catch (Exception e) {
      // 回滚预留，让补偿调度器把这次唤醒当作未送达重新投递
      applicationService.discardToolResultReservation(event);
      throw e;
    }
  }
}
