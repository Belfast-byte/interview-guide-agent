package interview.guide.modules.interview.agent.adaptive.application;

import interview.guide.modules.interview.agent.adaptive.algorithm.evidence.AlgorithmEvidenceConsumer;
import interview.guide.modules.interview.agent.adaptive.algorithm.judge.AlgorithmResultReadyHandler;
import interview.guide.modules.interview.agent.adaptive.algorithm.sandbox.SandboxExecution;
import interview.guide.modules.interview.agent.adaptive.observability.AlgorithmInterviewTelemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 将终态沙箱执行消费为正式 Evidence。
 */
@Component
@ConditionalOnProperty(
    prefix = "app.interview.adaptive-agent",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
class AdaptiveAlgorithmResultReadyHandler implements AlgorithmResultReadyHandler {

  private final AlgorithmEvidenceConsumer evidenceConsumer;
  private final AlgorithmInterviewTelemetry telemetry;

  @Override
  public void handle(SandboxExecution execution) {
    telemetry.resultReady(execution);
    if (!evidenceConsumer.consume(execution.id())) {
      telemetry.resultReadyDeduped();
    }
  }
}
